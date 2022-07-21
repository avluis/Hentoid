package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

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

public class EHentaiParser implements ImageListParser {

    public static final String MPV_LINK_CSS = "#gmid a[href*='/mpv/']";

    private static final String LIMIT_509_URL = "/509.gif";

    private final ParseProgress progress = new ParseProgress();

    static class MpvInfo {
        Integer gid;
        String mpvkey;
        String api_url;
        List<EHentaiImageMetadata> images;
        Integer pagecount;

        MpvImageInfo getImageInfo(int index) {
            return new MpvImageInfo(this, images.get(index), index + 1);
        }
    }

    static class MpvImageInfo {
        final int gid;
        final int pageNum;
        final String mpvkey;
        final String api_url;
        final EHentaiImageMetadata image;
        final int pagecount;

        public MpvImageInfo(MpvInfo info, EHentaiImageMetadata img, int pageNum) {
            gid = info.gid;
            this.pageNum = pageNum;
            mpvkey = info.mpvkey;
            api_url = info.api_url;
            image = img;
            pagecount = info.pagecount;
        }
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
                //result = loadMpv("https://e-hentai.org/mpv/530350/8b3c7e4a21/", headers, useHentoidAgent, useWebviewAgent);
                Elements elements = galleryDoc.select(MPV_LINK_CSS);
                if (!elements.isEmpty()) {
                    String mpvUrl = elements.get(0).attr("href");
                    try {
                        result = loadMpv(mpvUrl, headers, useHentoidAgent, useWebviewAgent, progress);
                    } catch (EmptyResultException e) {
                        result = loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent, progress);
                    }
                } else {
                    result = loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent, progress);
                }
            }
            progress.complete();

            // If the process has been halted manually, the result is incomplete and should not be returned as is
            if (progress.isProcessHalted()) throw new PreparationInterruptedException();
        } finally {
            EventBus.getDefault().unregister(this);
        }
        return result;
    }

    private static EHentaiImageResponse getMpvImage(
            @NonNull MpvImageInfo imageInfo,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent) throws EmptyResultException, IOException {
        EHentaiImageQuery query = new EHentaiImageQuery(imageInfo.gid, imageInfo.image.getKey(), imageInfo.mpvkey, imageInfo.pageNum);
        String jsonRequest = JsonHelper.serializeToJson(query, EHentaiImageQuery.class);
        Response response = HttpHelper.postOnlineResource(imageInfo.api_url, headers, true, useHentoidAgent, useWebviewAgent, jsonRequest, JsonHelper.JSON_MIME_TYPE);
        ResponseBody body = response.body();
        if (null == body)
            throw new EmptyResultException("API " + imageInfo.api_url + " returned an empty body");
        String bodyStr = body.string();
        if (!bodyStr.contains("{") || !bodyStr.contains("}"))
            throw new EmptyResultException("API " + imageInfo.api_url + " returned non-JSON data");

        return JsonHelper.jsonToObject(bodyStr, EHentaiImageResponse.class);
    }

    static List<ImageFile> loadMpv(
            @NonNull final String mpvUrl,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent,
            @NonNull ParseProgress progress) throws IOException, EmptyResultException {
        List<ImageFile> result = new ArrayList<>();

        // B.1- Open the MPV and parse gallery metadata
        MpvInfo mpvInfo = parseMpvPage(mpvUrl, headers, useHentoidAgent, useWebviewAgent);
        if (null == mpvInfo)
            throw new EmptyResultException("No exploitable data has been found on the multiple page viewer");

        int pageCount = Math.min(mpvInfo.pagecount, mpvInfo.images.size());

        for (int pageNum = 1; pageNum <= pageCount && !progress.isProcessHalted(); pageNum++) {
            // Get the URL of he 1st page as the cover
            if (1 == pageNum) {
                EHentaiImageResponse imageMetadata = getMpvImage(mpvInfo.getImageInfo(0), headers, useHentoidAgent, useWebviewAgent);
                result.add(ImageFile.newCover(imageMetadata.getUrl(), StatusContent.SAVED));
            }
            // Add page URLs to be read later by the downloader
            result.add(ImageFile.fromPageUrl(
                    pageNum,
                    JsonHelper.serializeToJson(mpvInfo.getImageInfo(pageNum - 1), MpvImageInfo.class),
                    StatusContent.SAVED, pageCount));
        }

        return result;
    }

    static List<ImageFile> loadClassic(
            @NonNull Content content,
            @NonNull final Document galleryDoc,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent,
            @NonNull ParseProgress progress) throws IOException {
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
            for (int i = 1; i < nbGalleryPages && !progress.isProcessHalted(); i++) {
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
        elements = doc.select("#i3.img");
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

    static Optional<ImageFile> parseBackupUrl(
            @NonNull String url,
            @NonNull Site site,
            @NonNull Map<String, String> requestHeaders,
            int order,
            int maxPages,
            Chapter chapter) throws Exception {
        List<Pair<String, String>> reqHeaders = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, url);
        Document doc = getOnlineDocument(url, reqHeaders, site.useHentoidAgent(), site.useWebviewAgent());
        if (doc != null) {
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains(LIMIT_509_URL))
                throw new LimitReachedException(site.getDescription() + " download points regenerate over time or can be bought if you're in a hurry");
            if (!imageUrl.isEmpty())
                return Optional.of(ParseHelper.urlToImageFile(imageUrl, order, maxPages, StatusContent.SAVED, chapter));
        }
        return Optional.empty();
    }

    @Nullable
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) throws Exception {
        return parseBackupUrl(url, Site.EHENTAI, requestHeaders, order, maxPages, chapter);
    }

    static ImmutablePair<String, Optional<String>> parseImagePageMpv(@NonNull String json, @NonNull List<Pair<String, String>> requestHeaders, @NonNull final Site site) throws IOException, LimitReachedException, EmptyResultException {
        MpvImageInfo mpvInfo = JsonHelper.jsonToObject(json, MpvImageInfo.class);
        EHentaiImageResponse imageMetadata = getMpvImage(mpvInfo, requestHeaders, site.useHentoidAgent(), site.useWebviewAgent());

        String imageUrl = imageMetadata.getUrl();
        // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
        if (imageUrl.contains(LIMIT_509_URL))
            throw new LimitReachedException("E(x)-hentai download points regenerate over time or can be bought on e(x)-hentai if you're in a hurry");

        return new ImmutablePair<>(imageUrl, Optional.empty());
    }

    static ImmutablePair<String, Optional<String>> parseImagePageClassic(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders, @NonNull final Site site) throws IOException, LimitReachedException, EmptyResultException {
        Document doc = getOnlineDocument(url, requestHeaders, site.useHentoidAgent(), site.useWebviewAgent());
        if (doc != null) {
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains(LIMIT_509_URL))
                throw new LimitReachedException("E(x)-hentai download points regenerate over time or can be bought on e(x)-hentai if you're in a hurry");

            Optional<String> backupUrl = getBackupPageUrl(doc, url);

            if (!imageUrl.isEmpty())
                return new ImmutablePair<>(imageUrl, backupUrl);
        }
        throw new EmptyResultException("Page contains no picture data : " + url);
    }

    static ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders, @NonNull final Site site) throws IOException, LimitReachedException, EmptyResultException {
        if (url.startsWith("http")) return parseImagePageClassic(url, requestHeaders, site);
        else return parseImagePageMpv(url, requestHeaders, site);
    }

    @Override
    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        return parseImagePage(url, requestHeaders, Site.EHENTAI);
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
                progress.haltProcess();
                break;
            case DownloadEvent.Type.EV_COMPLETE:
            case DownloadEvent.Type.EV_PREPARATION:
            case DownloadEvent.Type.EV_PROGRESS:
            case DownloadEvent.Type.EV_UNPAUSE:
            case DownloadEvent.Type.EV_INTERRUPT_CONTENT:
            default:
                // Other events aren't handled here
                break;
        }
    }
}
