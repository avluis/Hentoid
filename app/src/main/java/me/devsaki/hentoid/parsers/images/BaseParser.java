package me.devsaki.hentoid.parsers.images;

import android.webkit.URLUtil;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.parsers.ParseHelper;
import timber.log.Timber;

public abstract class BaseParser implements ImageListParser {

    private final ParseProgress progress = new ParseProgress();

    protected abstract List<String> parseImages(Content content) throws Exception;

    public List<ImageFile> parseImageList(Content content) throws Exception {
        String readerUrl = content.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        List<String> imgUrls = parseImages(content);
        List<ImageFile> images = ParseHelper.urlsToImageFiles(imgUrls);

        Timber.d("%s", images);

        return images;
    }

    public ImageFile parseBackupUrl(String url, int order) {
        return null;
    }

    void progressStart(int maxSteps) {
        progress.progressStart(maxSteps);
    }

    void progressPlus() {
        progress.progressPlus();
    }

    void progressComplete() {
        progress.progressComplete();
    }
}
