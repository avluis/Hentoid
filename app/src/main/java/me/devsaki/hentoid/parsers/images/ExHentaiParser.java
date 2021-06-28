package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
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

import static me.devsaki.hentoid.parsers.images.EHentaiParser.MPV_LINK_CSS;
import static me.devsaki.hentoid.parsers.images.EHentaiParser.getCookieStr;
import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class ExHentaiParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    private boolean processHalted = false;


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
            boolean useHentoidAgent = Site.EXHENTAI.useHentoidAgent();
            boolean useWebviewAgent = Site.EXHENTAI.useWebviewAgent();
            Document galleryDoc = getOnlineDocument(content.getGalleryUrl(), headers, useHentoidAgent, useWebviewAgent);
            if (galleryDoc != null) {
                // Detect if multipage viewer is on
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

    private List<ImageFile> loadMpv(
            @NonNull Content content,
            @NonNull final String mpvUrl,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent) throws IOException, EmptyResultException {
        List<ImageFile> result = new ArrayList<>();

        // B.1- Open the MPV and parse gallery metadata
        EHentaiParser.MpvInfo mpvInfo = EHentaiParser.parseMpvPage(mpvUrl, headers, useHentoidAgent, useWebviewAgent);
        if (null == mpvInfo)
            throw new EmptyResultException("No exploitable data has been found on the multiple page viewer");

        int pageCount = Math.min(mpvInfo.pagecount, mpvInfo.images.size());
        progress.start(content.getId(), pageCount);

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

        EHentaiParser.fetchPageUrls(galleryDoc, pageUrls);

        if (nbGalleryPages > 1) {
            for (int i = 1; i < nbGalleryPages && !processHalted; i++) {
                Document pageDoc = getOnlineDocument(content.getGalleryUrl() + "/?p=" + i, headers, useHentoidAgent, useWebviewAgent);
                if (pageDoc != null) EHentaiParser.fetchPageUrls(pageDoc, pageUrls);
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
            ImageFile img = EHentaiParser.parsePicturePage(pageUrl, headers, useHentoidAgent, useWebviewAgent, order++, pageUrls.size());
            if (img != null) result.add(img);
            progress.advance();
        }

        return result;
    }

    @Nullable
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) throws Exception {
        List<Pair<String, String>> reqHeaders = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, url);
        Document doc = getOnlineDocument(url, reqHeaders, Site.EXHENTAI.useHentoidAgent(), Site.EXHENTAI.useWebviewAgent());
        if (doc != null) {
            String imageUrl = EHentaiParser.getDisplayedImageUrl(doc).toLowerCase();
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains("/509.gif"))
                throw new LimitReachedException("Exhentai download points regenerate over time or can be bought on e-hentai if you're in a hurry");
            if (!imageUrl.isEmpty())
                return Optional.of(ParseHelper.urlToImageFile(imageUrl, order, maxPages, StatusContent.SAVED, chapter));
        }
        return Optional.empty();
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
