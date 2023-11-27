package me.devsaki.hentoid.parsers.images;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.annimon.stream.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadCommandEvent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public abstract class BaseImageListParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();
    protected AtomicBoolean processHalted = new AtomicBoolean(false);
    protected String processedUrl = "";

    protected abstract boolean isChapterUrl(@NonNull String url);

    protected abstract List<String> parseImages(@NonNull Content content) throws Exception;

    protected abstract List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception;

    public List<ImageFile> parseImageList(@NonNull Content onlineContent, @NonNull Content storedContent) throws Exception {
        return parseImageListImpl(onlineContent, storedContent);
    }

    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        return parseImageListImpl(content, null);
    }

    public List<ImageFile> parseImageList(@NonNull Content content, @NonNull String url) throws Exception {
        if (isChapterUrl(url)) return parseChapterImageListImpl(url, content);
        else return parseImageListImpl(content, null);
    }

    protected List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        String readerUrl = onlineContent.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        processedUrl = onlineContent.getGalleryUrl();
        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            List<String> imgUrls = parseImages(onlineContent);
            result = ParseHelper.urlsToImageFiles(imgUrls, StatusContent.SAVED, onlineContent.getCoverImageUrl(), null);
            ParseHelper.setDownloadParams(result, onlineContent.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        return result;
    }

    public List<ImageFile> parseChapterImageListImpl(@NonNull String url, @NonNull Content content) throws Exception {
        if (!URLUtil.isValidUrl(url))
            throw new IllegalArgumentException("Invalid gallery URL : " + url);

        Timber.d("Chapter URL: %s", url);

        processedUrl = url;
        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            List<String> imgUrls = parseImages(url, content.getDownloadParams(), null);
            result = ParseHelper.urlsToImageFiles(imgUrls, StatusContent.SAVED, null, null);
            ParseHelper.setDownloadParams(result, content.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        return result;
    }

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, @NonNull Map<String, String> requestHeaders, int order, int maxPages, Chapter chapter) {
        // Default behaviour; this class does not use backup URLs
        ImageFile img = ImageFile.fromImageUrl(order, url, StatusContent.SAVED, maxPages);
        if (chapter != null) img.setChapter(chapter);
        return Optional.of(img);
    }

    public ImmutablePair<String, Optional<String>> parseImagePage(@NonNull String url, @NonNull List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        throw new NotImplementedException("Parser does not implement parseImagePage");
    }

    @Override
    public String getAltUrl(@NonNull String url) {
        return "";
    }

    void progressStart(@NonNull Content onlineContent, @Nullable Content storedContent, int maxSteps) {
        if (progress.hasStarted()) return;
        long storedId = (storedContent != null) ? storedContent.getId() : -1;
        progress.start(onlineContent.getId(), storedId, maxSteps);
    }

    // TODO method to free resources after caller decides work's done

    void progressPlus() {
        progress.advance();
    }

    void progressComplete() {
        progress.complete();
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    public void onDownloadCommand(DownloadCommandEvent event) {
        switch (event.getType()) {
            case EV_PAUSE:
            case EV_CANCEL:
            case EV_SKIP:
                processHalted.set(true);
                break;
            case EV_INTERRUPT_CONTENT:
                if (event.getContent() != null && event.getContent().getGalleryUrl().equals(processedUrl)) {
                    processHalted.set(true);
                    processedUrl = "";
                }
                break;
            case EV_UNPAUSE:
            default:
                // Other events aren't handled here
        }
    }

    protected List<Pair<String, String>> fetchHeaders(@NonNull Content content) {
        return fetchHeaders(content.getGalleryUrl(), content.getDownloadParams());
    }

    protected List<Pair<String, String>> fetchHeaders(@NonNull String url, String downloadParams) {
        List<Pair<String, String>> headers = new ArrayList<>();
        if (downloadParams != null) ParseHelper.addSavedCookiesToHeader(downloadParams, headers);
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, url));
        return headers;
    }
}
