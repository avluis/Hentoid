package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.parsers.images.EHentaiParser.MPV_LINK_CSS;
import static me.devsaki.hentoid.parsers.images.EHentaiParser.getCookieStr;
import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
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
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ExHentaiParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();


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
            if (progress.isProcessHalted()) throw new PreparationInterruptedException();
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
        progress.start(content.getId(), -1, pageCount);

        // B.2- Call the API to get the pictures URL
        for (int pageNum = 1; pageNum <= pageCount && !progress.isProcessHalted(); pageNum++) {
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
            if (0 == pageNum % 10) Helper.pause(750);
        }

        return result;
    }

    private List<ImageFile> loadClassic(
            @NonNull Content content,
            @NonNull final Document galleryDoc,
            @NonNull final List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent) throws IOException {
        return EHentaiParser.loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent, progress);
    }

    @Nullable
    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) throws Exception {
        return EHentaiParser.parseBackupUrl(url, Site.EXHENTAI, requestHeaders, order, maxPages, chapter);
    }

    @Override
    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        return EHentaiParser.parseImagePage(url, requestHeaders, Site.EXHENTAI);
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
            default:
                // Other events aren't handled here
        }
    }
}
