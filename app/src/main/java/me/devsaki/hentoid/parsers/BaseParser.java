package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import timber.log.Timber;

public abstract class BaseParser implements ContentParser {

    private int currentStep;
    int maxSteps;

    protected abstract List<String> parseImages(Content content) throws Exception;

    public List<ImageFile> parseImageList(Content content) throws Exception {
        String readerUrl = content.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl)) {
            throw new Exception("Invalid gallery URL : " + readerUrl);
        }
        Timber.d("Gallery URL: %s", readerUrl);

        currentStep = -1;
        List<String> imgUrls = parseImages(content);
        List<ImageFile> images = ParseHelper.urlsToImageFiles(imgUrls);

        Timber.d("%s", images);

        return images;
    }

    void progressPlus()
    {
        signalProgress(++currentStep, maxSteps);
    }

    void progressComplete()
    {
        signalProgress(maxSteps, maxSteps);
    }

    private void signalProgress(int current, int max)
    {
        EventBus.getDefault().post(new DownloadPreparationEvent(current, max));
    }
}
