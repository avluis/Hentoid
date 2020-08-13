package me.devsaki.hentoid.parsers.images;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import timber.log.Timber;

public abstract class BaseParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    protected abstract List<String> parseImages(@NonNull Content content) throws Exception;

    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        String readerUrl = content.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        List<String> imgUrls = parseImages(content);
        List<ImageFile> result = ParseHelper.urlsToImageFiles(imgUrls, content.getCoverImageUrl(), StatusContent.SAVED);

        // Copy the content's download params to the images
        String downloadParamsStr = content.getDownloadParams();
        if (downloadParamsStr != null && downloadParamsStr.length() > 2) {
            for (ImageFile i : result) i.setDownloadParams(downloadParamsStr);
        }

        Timber.d("%s", result);

        return result;
    }

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, int order, int maxPages) {
        return Optional.of(new ImageFile(order, url, StatusContent.SAVED, maxPages));
    }

    void progressStart(int maxSteps) {
        progress.start(maxSteps);
    }

    void progressPlus() {
        progress.advance();
    }

    void progressComplete() {
        progress.complete();
    }
}
