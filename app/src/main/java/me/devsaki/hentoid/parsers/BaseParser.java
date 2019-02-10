package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import timber.log.Timber;

public abstract class BaseParser implements ContentParser {

    protected abstract List<String> parseImages(Content content) throws Exception;

    public List<String> parseImageList(Content content) {
        String readerUrl = content.getReaderUrl();
        List<String> imgUrls = Collections.emptyList();

        if (!URLUtil.isValidUrl(readerUrl)) {
            Timber.e("Invalid gallery URL : %s", readerUrl);
            return imgUrls;
        }
        Timber.d("Gallery URL: %s", readerUrl);

        try {
            imgUrls = parseImages(content);
        } catch (IOException e) {
            Timber.e(e, "I/O Error while attempting to connect to: %s", readerUrl);
        } catch (Exception e) {
            Timber.e(e, "Unexpected Error while attempting to connect to: %s", readerUrl);
        }
        Timber.d("%s", imgUrls);

        return imgUrls;
    }

}
