package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;
import com.squareup.moshi.Types;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.json.sources.EHentaiImageMetadata;
import me.devsaki.hentoid.json.sources.EHentaiImageQuery;
import me.devsaki.hentoid.json.sources.EHentaiImageResponse;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class EHentaiParser implements ImageListParser {

    public static final String MPV_LINK_CSS = "#gmid a[href*='/mpv/']";

    private final ParseProgress progress = new ParseProgress();

    private boolean processHalted = false;

    static class MpvInfo {
        Integer gid;
        String mpvkey;
        String api_url;
        List<EHentaiImageMetadata> images;
        Integer pagecount;
    }


    @Override
    public List<ImageFile> parseImageList(@NonNull Content onlineContent, @NonNull Content storedContent) throws Exception {
        return parseImageList(onlineContent);
    }

    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        EventBus.getDefault().register(this);

        List<ImageFile> result = Collections.emptyList();
        try {
            // Retrieve and set cookies (optional; e-hentai can work without cookies even though certain galleries are unreachable)
            String cookieStr = getCookieStr(content);

            List<Pair<String, String>> headers = new ArrayList<>();
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
            headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, content.getSite().getUrl()));
            headers.add(new Pair<>(HttpHelper.HEADER_ACCEPT_KEY, "*/*"));

            /*
             * A/ Without multipage viewer
             *    A.1- Detect the number of pages of the gallery
             *
             *    A.2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
             *
             *    A.3- Open all pages and grab the URL of the displayed image
             *
             * B/ With multipage viewer
             *    B.1- Open the MPV and parse gallery metadata
             *
             *    B.2- Call the API to get the pictures URL
             */
            boolean useHentoidAgent = Site.EHENTAI.useHentoidAgent();
            boolean useWebviewAgent = Site.EHENTAI.useWebviewAgent();
            Document galleryDoc = getOnlineDocument(content.getGalleryUrl(), headers, useHentoidAgent, useWebviewAgent);
            if (galleryDoc != null) {
                // Detect if multipage viewer is on
//                result = loadMpv("https://e-hentai.org/mpv/530350/8b3c7e4a21/", headers, useHentoidAgent);
                Elements elements = galleryDoc.select(MPV_LINK_CSS);
                if (!elements.isEmpty()) {
                    String mpvUrl = elements.get(0).attr("href");
                    try {
                        result = loadMpv(content, mpvUrl, headers, useHentoidAgent, useWebviewAgent);
                    } catch (EmptyResultException e) {
                        result = loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent);
                    }
                } else {
                    result = loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent);
                }
            }


            progress.complete();

            // If the process has been halted manually, the result is incomplete and should not be returned as is
            if (processHalted) throw new PreparationInterruptedException();
        } finally {
            EventBus.getDefault().unregister(this);
        }
        return result;
    }

    @SuppressWarnings("BusyWait")
    private List<ImageFile> loadMpv(
            @NonNull Content content,
            @NonNull final String mpvUrl,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent) throws IOException, EmptyResultException {
        List<ImageFile> result = new ArrayList<>();

        // B.1- Open the MPV and parse gallery metadata
        MpvInfo mpvInfo = parseMpvPage(mpvUrl, headers, useHentoidAgent, useWebviewAgent);
        if (null == mpvInfo)
            throw new EmptyResultException("No exploitable data has been found on the multiple page viewer");

        int pageCount = Math.min(mpvInfo.pagecount, mpvInfo.images.size());
        progress.start(content.getId(), -1, pageCount);

        // B.2- Call the API to get the pictures URL
        for (int pageNum = 1; pageNum <= pageCount && !processHalted; pageNum++) {
            EHentaiImageQuery query = new EHentaiImageQuery(mpvInfo.gid, mpvInfo.images.get(pageNum - 1).getKey(), mpvInfo.mpvkey, pageNum);
            String jsonRequest = JsonHelper.serializeToJson(query, EHentaiImageQuery.class);
            Response response = HttpHelper.postOnlineResource(mpvInfo.api_url, headers, true, useHentoidAgent, useWebviewAgent, jsonRequest, JsonHelper.JSON_MIME_TYPE);
            ResponseBody body = response.body();
            if (null == body)
                throw new EmptyResultException("API " + mpvInfo.api_url + " returned an empty body");
            String bodyStr = body.string();
            if (!bodyStr.contains("{") || !bodyStr.contains("}"))
                throw new EmptyResultException("API " + mpvInfo.api_url + " returned non-JSON data");

            EHentaiImageResponse imageMetadata = JsonHelper.jsonToObject(bodyStr, EHentaiImageResponse.class);
            if (1 == pageNum)
                result.add(ImageFile.newCover(imageMetadata.getUrl(), StatusContent.SAVED));
            result.add(ParseHelper.urlToImageFile(imageMetadata.getUrl(), pageNum, pageCount, StatusContent.SAVED));
            progress.advance();
            // Emulate JS loader
            if (0 == pageNum % 10) {
                try {
                    Thread.sleep(750);
                } catch (InterruptedException e) {
                    Timber.w(e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        return result;
    }

    private List<ImageFile> loadClassic(
            @NonNull Content content,
            @NonNull final Document galleryDoc,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent) throws IOException {
        List<ImageFile> result = new ArrayList<>();

        // A.1- Detect the number of pages of the gallery
        Elements elements = galleryDoc.select("table.ptt a");
        if (elements.isEmpty()) return result;

        int tabId = (1 == elements.size()) ? 0 : elements.size() - 2;
        int nbGalleryPages = Integer.parseInt(elements.get(tabId).text());

        progress.start(content.getId(), -1, nbGalleryPages);

        // 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
        List<String> pageUrls = new ArrayList<>();

        fetchPageUrls(galleryDoc, pageUrls);

        if (nbGalleryPages > 1) {
            for (int i = 1; i < nbGalleryPages && !processHalted; i++) {
                Document pageDoc = getOnlineDocument(content.getGalleryUrl() + "/?p=" + i, headers, useHentoidAgent, useWebviewAgent);
                if (pageDoc != null) fetchPageUrls(pageDoc, pageUrls);
                progress.advance();
            }
        }

        // 3- Open all pages and
        //    - grab the URL of the displayed image
        //    - grab the alternate URL of the "Click here if the image fails loading" link
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));

        int order = 1;
        for (String pageUrl : pageUrls) {
            result.add(ImageFile.fromPageUrl(order++, pageUrl, StatusContent.SAVED, pageUrls.size()));
        }

        return result;
    }

    static void fetchPageUrls(@Nonnull Document doc, List<String> pageUrls) {
        Elements imageLinks = doc.select(".gdtm a"); // Normal thumbs
        if (imageLinks.isEmpty())
            imageLinks = doc.select(".gdtl a"); // Large thumbs
        if (imageLinks.isEmpty())
            imageLinks = doc.select("#gdt a"); // Universal, ID-based
        for (Element e : imageLinks) pageUrls.add(e.attr("href"));
    }

    static String getDisplayedImageUrl(@Nonnull Document doc) {
        Elements elements = doc.select("img#img");
        if (!elements.isEmpty()) {
            Element e = elements.first();
            if (e != null) return ParseHelper.getImgSrc(e);
        }
        return "";
    }

    static Optional<String> getBackupPageUrl(@NonNull Document doc, @NonNull String queryUrl) {
        // "Click here if the image fails loading" link
        // -> add backup info to the image's downloadParams
        Elements elements = doc.select("#loadfail");
        if (!elements.isEmpty()) {
            Element e = elements.first();
            if (e != null) {
                String arg = e.attr("onclick");
                // Get the argument between 's
                int quoteBegin = arg.indexOf('\'');
                int quoteEnd = arg.indexOf('\'', quoteBegin + 1);
                arg = arg.substring(quoteBegin + 1, quoteEnd);
                // Get the query URL
                String backupUrl = queryUrl;
                if (backupUrl.contains("?")) backupUrl += "&";
                else backupUrl += "?";
                backupUrl += "nl=" + arg;
                // Get the final URL
                if (URLUtil.isValidUrl(backupUrl)) return Optional.of(backupUrl);
            }
        }
        return Optional.empty();
    }

    @Nullable
    static MpvInfo parseMpvPage(@NonNull final String url,
                                @NonNull final List<Pair<String, String>> headers,
                                boolean useHentoidAgent,
                                boolean useWebviewAgent) throws IOException {
        MpvInfo result = null;
        Document doc = getOnlineDocument(url, headers, useHentoidAgent, useWebviewAgent);
        if (doc != null) {
            List<Element> scripts = doc.select("script");
            for (Element script : scripts) {
                String scriptStr = script.toString();
                if (scriptStr.contains("pagecount")) {
                    result = new MpvInfo();
                    String[] scriptLines = scriptStr.split("\\n");
                    for (String line : scriptLines) {
                        String[] parts = line.replace("  ", " ").replace(";", "").trim().split("=");
                        if (parts.length > 1) {
                            if (parts[0].contains("var gid")) {
                                result.gid = Integer.parseInt(parts[1].replace("\"", "").trim());
                            } else if (parts[0].contains("var pagecount")) {
                                result.pagecount = Integer.parseInt(parts[1].replace("\"", "").trim());
                            } else if (parts[0].contains("var mpvkey")) {
                                result.mpvkey = parts[1].replace("\"", "").trim();
                            } else if (parts[0].contains("var api_url")) {
                                result.api_url = parts[1].replace("\"", "").trim();
                            } else if (parts[0].contains("var imagelist")) {
                                result.images = JsonHelper.jsonToObject(parts[1].trim(), Types.newParameterizedType(List.class, EHentaiImageMetadata.class));
                            }
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    @Nullable
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) throws Exception {
        List<Pair<String, String>> reqHeaders = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, url);
        Document doc = getOnlineDocument(url, reqHeaders, Site.EHENTAI.useHentoidAgent(), Site.EHENTAI.useWebviewAgent());
        if (doc != null) {
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains("/509.gif"))
                throw new LimitReachedException("Bandwidth limit reached");
            if (!imageUrl.isEmpty())
                return Optional.of(ParseHelper.urlToImageFile(imageUrl, order, maxPages, StatusContent.SAVED, chapter));
        }
        return Optional.empty();
    }

    static ImmutablePair<String, Optional<String>> parseImagePageEh(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        Document doc = getOnlineDocument(url, requestHeaders, Site.EHENTAI.useHentoidAgent(), Site.EHENTAI.useWebviewAgent());
        if (doc != null) {
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains("/509.gif"))
                throw new LimitReachedException("E(x)-hentai download points regenerate over time or can be bought on e(x)-hentai if you're in a hurry");

            Optional<String> backupUrl = getBackupPageUrl(doc, url);

            if (!imageUrl.isEmpty())
                return new ImmutablePair<>(imageUrl, backupUrl);
        }
        throw new EmptyResultException("Page contains no picture data : " + url);
    }

    @Override
    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        return parseImagePageEh(url, requestHeaders);
    }

    /**
     * Retrieve cookies (optional; e-hentai can work without cookies even though certain galleries are unreachable)
     *
     * @param content Content to retrieve cookies from
     * @return Cookie string
     */
    public static String getCookieStr(@NonNull final Content content) {
        String cookieStr = ParseHelper.getSavedCookieStr(content.getDownloadParams());
        if (cookieStr.isEmpty())
            return "nw=1"; // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        else return cookieStr;
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            case DownloadEvent.Type.EV_PAUSE:
            case DownloadEvent.Type.EV_CANCEL:
            case DownloadEvent.Type.EV_SKIP:
                processHalted = true;
                break;
            case DownloadEvent.Type.EV_COMPLETE:
            case DownloadEvent.Type.EV_PREPARATION:
            case DownloadEvent.Type.EV_PROGRESS:
            case DownloadEvent.Type.EV_UNPAUSE:
            default:
                // Other events aren't handled here
        }
    }
}
