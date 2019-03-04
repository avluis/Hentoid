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

    public List<ImageFile> parseImageList(Content content) throws Exception {
        String readerUrl = content.getReaderUrl();
        List<ImageFile> images = Collections.emptyList();

        if (!URLUtil.isValidUrl(readerUrl)) {
            throw new Exception("Invalid gallery URL : " + readerUrl);
        }
        Timber.d("Gallery URL: %s", readerUrl);

        List<String> imgUrls = parseImages(content);
        images = ParseHelper.urlsToImageFiles(imgUrls);

        Timber.d("%s", images);

        return images;
    }

}
