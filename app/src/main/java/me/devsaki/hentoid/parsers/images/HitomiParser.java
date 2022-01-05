package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.views.HitomiBackgroundWebView;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser extends BaseImageListParser {

    public List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        String pageUrl = onlineContent.getReaderUrl();

        Document doc = getOnlineDocument(pageUrl);
        if (null == doc) throw new ParseException("Document unreachable : " + pageUrl);

        Timber.d("Parsing: %s", pageUrl);

        List<ImageFile> result = new ArrayList<>();
        result.add(ImageFile.newCover(onlineContent.getCoverImageUrl(), StatusContent.SAVED));

        String galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + onlineContent.getUniqueSiteId() + ".js";

        // Get the gallery JSON
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, pageUrl));
        Response response = HttpHelper.getOnlineResource(galleryJsonUrl, headers, Site.HITOMI.useMobileAgent(), Site.HITOMI.useHentoidAgent(), Site.HITOMI.useWebviewAgent());

        ResponseBody body = response.body();
        if (null == body) throw new IOException("Empty body");
        String galleryInfo = body.string();

        final Object _lock = new Object();
        final AtomicReference<String> imagesStr = new AtomicReference<>();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
            HitomiBackgroundWebView wv = new HitomiBackgroundWebView(HentoidApp.getInstance(), Site.HITOMI);
            wv.loadUrl(pageUrl, () -> {
                Timber.v(">> loaded wv");
                wv.evaluateJavascript(getJsPagesScript(galleryInfo), s -> {
                    imagesStr.set(s);
                    synchronized (_lock) {
                        _lock.notifyAll();
                    }
                });
            });
            Timber.i(">> loading wv");
        });

        synchronized (_lock) {
            Timber.w("Waiting for lock");
            _lock.wait();
        }

        Map<String, String> downloadParams = new HashMap<>();
        // Add referer information to downloadParams for future image download
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, pageUrl);
        String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);

        String jsResult = imagesStr.get().replace("\"[", "[").replace("]\"", "]").replace("\\\"", "\"");
        Timber.w("JSD RESULT = %s", jsResult);
        List<String> imageUrls = JsonHelper.jsonToObject(jsResult, JsonHelper.LIST_STRINGS);

        int order = 1;
        for (String s : imageUrls) {
            ImageFile img = ParseHelper.urlToImageFile(s, order++, imageUrls.size(), StatusContent.SAVED);
            img.setDownloadParams(downloadParamsStr);
            result.add(img);
        }

        return result;
    }


    // TODO optimize
    private String getJsPagesScript(@NonNull String galleryInfo) {
        StringBuilder sb = new StringBuilder();
        FileHelper.getAssetAsString(HentoidApp.getInstance().getAssets(), "hitomi_pages.js", sb);
        return sb.toString().replace("$galleryInfo", galleryInfo).replace("$webp", Preferences.isDlHitomiWebp()?"true":"false");
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageListImpl is overriden directly
        return null;
    }
}
