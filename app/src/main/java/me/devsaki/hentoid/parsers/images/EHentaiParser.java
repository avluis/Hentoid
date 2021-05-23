package me.devsaki.hentoid.parsers.images;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;
import com.squareup.moshi.Types;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class EHentaiParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    private boolean processHalted = false;

    static class MpvInfo {
        Integer gid;
        String mpvkey;
        String api_url;
        List<EHentaiImageMetadata> images;
        Integer pagecount;
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
                Elements elements = galleryDoc.select(".gm a[href*='/mpv/']");
                if (!elements.isEmpty()) {
                    String mpvUrl = elements.get(0).attr("href");
                    result = loadMpv(content, mpvUrl, headers, useHentoidAgent, useWebviewAgent);
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
        progress.start(content.getId(), pageCount);

        // B.2- Call the API to get the pictures URL
        for (int pageNum = 1; pageNum <= pageCount && !processHalted; pageNum++) {
            EHentaiImageQuery query = new EHentaiImageQuery(mpvInfo.gid, mpvInfo.images.get(pageNum - 1).getKey(), mpvInfo.mpvkey, pageNum);
            String jsonRequest = JsonHelper.serializeToJson(query, EHentaiImageQuery.class);
            Response response = HttpHelper.postOnlineResource(mpvInfo.api_url, headers, useHentoidAgent, useWebviewAgent, jsonRequest, JsonHelper.JSON_MIME_TYPE);
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
            boolean useWebviewAgent) throws IOException, LimitReachedException {
        List<ImageFile> result = new ArrayList<>();

        // A.1- Detect the number of pages of the gallery
        Elements elements = galleryDoc.select("table.ptt a");
        if (null == elements || elements.isEmpty()) return result;

        int tabId = (1 == elements.size()) ? 0 : elements.size() - 2;
        int nbGalleryPages = Integer.parseInt(elements.get(tabId).text());

        progress.start(content.getId(), nbGalleryPages + content.getQtyPages());

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
            if (processHalted) break;
            ImageFile img = parsePicturePage(pageUrl, headers, useHentoidAgent, useWebviewAgent, order++, pageUrls.size());
            if (img != null) result.add(img);
            progress.advance();
        }

        return result;
    }

    static void fetchPageUrls(@Nonnull Document doc, List<String> pageUrls) {
        Elements imageLinks = doc.select(".gdtm a"); // Normal thumbs
        if (null == imageLinks || imageLinks.isEmpty())
            imageLinks = doc.select(".gdtl a"); // Large thumbs
        if (null == imageLinks || imageLinks.isEmpty())
            imageLinks = doc.select("#gdt a"); // Universal, ID-based
        for (Element e : imageLinks) pageUrls.add(e.attr("href"));
    }

    static String getDisplayedImageUrl(@Nonnull Document doc) {
        Elements elements = doc.select("img#img");
        if (!elements.isEmpty()) {
            Element e = elements.first();
            return e.attr("src");
        }
        return "";
    }

    @Nullable
    static ImageFile parsePicturePage(
            @NonNull final String url,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent,
            int order,
            int nbPages
    ) throws IOException, LimitReachedException {
        ImageFile img = null;
        Document doc = getOnlineDocument(url, headers, useHentoidAgent, useWebviewAgent);
        if (doc != null) {
            // Displayed image
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            if (!imageUrl.isEmpty()) {
                // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
                if (imageUrl.contains("/509.gif"))
                    throw new LimitReachedException("E(x)-hentai download points regenerate over time or can be bought on e(x)-hentai if you're in a hurry");
                img = ParseHelper.urlToImageFile(imageUrl, order, nbPages, StatusContent.SAVED);

                // "Click here if the image fails loading" link
                // -> add backup info to the image's downloadParams
                Elements elements = doc.select("#loadfail");
                if (!elements.isEmpty()) {
                    Element e = elements.first();
                    String arg = e.attr("onclick");
                    // Get the argument between 's
                    int quoteBegin = arg.indexOf('\'');
                    int quoteEnd = arg.indexOf('\'', quoteBegin + 1);
                    arg = arg.substring(quoteBegin + 1, quoteEnd);
                    // Get the query URL
                    String backupUrl = url;
                    if (backupUrl.contains("?")) backupUrl += "&";
                    else backupUrl += "?";
                    backupUrl += "nl=" + arg;
                    // Get the final URL
                    if (URLUtil.isValidUrl(backupUrl)) {
                        Map<String, String> targetDownloadParams = new HashMap<>();
                        targetDownloadParams.put("backupUrl", backupUrl);
                        String downloadParamsStr = JsonHelper.serializeToJson(targetDownloadParams, JsonHelper.MAP_STRINGS);
                        img.setDownloadParams(downloadParamsStr);
                    }
                }
            }
        }
        return img;
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
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages) throws Exception {
        List<Pair<String, String>> reqHeaders = HttpHelper.webResourceHeadersToOkHttpHeaders(requestHeaders, url);
        Document doc = getOnlineDocument(url, reqHeaders, Site.EHENTAI.useHentoidAgent(), Site.EHENTAI.useWebviewAgent());
        if (doc != null) {
            String imageUrl = getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains("/509.gif"))
                throw new LimitReachedException("Bandwidth limit reached");
            if (!imageUrl.isEmpty())
                return Optional.of(ParseHelper.urlToImageFile(imageUrl, order, maxPages, StatusContent.SAVED));
        }
        return Optional.empty();
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
            case DownloadEvent.EV_PAUSE:
            case DownloadEvent.EV_CANCEL:
            case DownloadEvent.EV_SKIP:
                processHalted = true;
                break;
            default:
                // Other events aren't handled here
        }
    }
}
