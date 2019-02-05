package me.devsaki.hentoid.parsers;

import android.util.Pair;
import android.webkit.URLUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import timber.log.Timber;

public abstract class BaseParser implements ContentParser {

    private static final int TIMEOUT = 30000; // 30 seconds

    protected abstract List<String> parseImages(Content content) throws Exception;

    @Nullable
    Document getOnlineDocument(String url) throws IOException {
        return getOnlineDocument(url, null);
    }

    @Nullable
    Document getOnlineDocument(String url, List<Pair<String, String>> headers) throws IOException {
        OkHttpClient okHttp = OkHttpClientSingleton.getInstance(TIMEOUT);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) for (Pair<String, String> header : headers)
            requestBuilder.addHeader(header.first, header.second);
        Request request = requestBuilder.get().build();
        ResponseBody body = okHttp.newCall(request).execute().body();
        if (body != null) {
            return Jsoup.parse(body.string());
        }
        return null;
    }

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
