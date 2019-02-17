package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import timber.log.Timber;

public abstract class BaseParser implements ContentParser {

    protected abstract List<String> parseImages(Content content) throws Exception;

    public List<ImageFile> parseImageList(Content content) {
        String readerUrl = content.getReaderUrl();
        List<ImageFile> images = Collections.emptyList();

        if (!URLUtil.isValidUrl(readerUrl)) {
            Timber.e("Invalid gallery URL : %s", readerUrl);
            return images;
        }
        Timber.d("Gallery URL: %s", readerUrl);

        try {
            List<String> imgUrls = parseImages(content);
            images = ParseHelper.urlsToImageFiles(imgUrls);
        } catch (IOException e) {
            Timber.e(e, "I/O Error while attempting to connect to %s", readerUrl);
        } catch (Exception e) {
            Timber.e(e, "Unexpected Error while attempting to connect to %s", readerUrl);
        }
        Timber.d("%s", images);

        return images;
    }

}
