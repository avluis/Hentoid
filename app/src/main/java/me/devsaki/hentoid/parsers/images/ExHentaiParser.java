package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.parsers.images.EHentaiParser.MPV_LINK_CSS;
import static me.devsaki.hentoid.parsers.images.EHentaiParser.getCookieStr;
import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.core.util.Pair;

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
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;

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
                        result = EHentaiParser.loadMpv(mpvUrl, headers, useHentoidAgent, useWebviewAgent, progress);
                    } catch (EmptyResultException e) {
                        result = EHentaiParser.loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent, progress);
                    }
                } else {
                    result = EHentaiParser.loadClassic(content, galleryDoc, headers, useHentoidAgent, useWebviewAgent, progress);
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
            case DownloadEvent.Type.EV_COMPLETE:
            case DownloadEvent.Type.EV_PREPARATION:
            case DownloadEvent.Type.EV_PROGRESS:
            case DownloadEvent.Type.EV_UNPAUSE:
            case DownloadEvent.Type.EV_INTERRUPT_CONTENT:
            default:
                // Other events aren't handled here
        }
    }
}
