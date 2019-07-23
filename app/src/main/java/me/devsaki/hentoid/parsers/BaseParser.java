package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import timber.log.Timber;

public abstract class BaseParser implements ImageListParser {

    private int currentStep;
    private int maxSteps;

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

    void progressStart(int maxSteps) {
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(currentStep, maxSteps);
    }

    void progressPlus() {
        ParseHelper.signalProgress(++currentStep, maxSteps);
    }

    void progressComplete() {
        ParseHelper.signalProgress(maxSteps, maxSteps);
    }
}
