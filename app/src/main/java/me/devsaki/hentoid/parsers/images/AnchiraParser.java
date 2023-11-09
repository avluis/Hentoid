package me.devsaki.hentoid.parsers.images;

import android.os.Handler;
import android.os.Looper;
import android.webkit.URLUtil;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.views.AnchiraBackgroundWebView;
import timber.log.Timber;

/**
 * Handles parsing of content from anchira.to
 */
public class AnchiraParser extends BaseImageListParser {

    @Override
    public List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        String readerUrl = onlineContent.getReaderUrl();
        processedUrl = onlineContent.getGalleryUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            result = parseImageListWithWebview(onlineContent);
            ParseHelper.setDownloadParams(result, onlineContent.getSite().getUrl());
        } catch (Exception e) {
            Helper.logException(e);
            result = new ArrayList<>();
        } finally {
            EventBus.getDefault().unregister(this);
        }

        return result;
    }

    public List<ImageFile> parseImageListWithWebview(@NonNull Content onlineContent) throws Exception {
        String pageUrl = onlineContent.getGalleryUrl();

        // Add referer information to downloadParams for future image download
        Map<String, String> downloadParams = new HashMap<>();
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, pageUrl);
        String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);

        // Get pages URL
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicReference<String> imagesStr = new AtomicReference<>();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
            AnchiraBackgroundWebView anchiraWv = new AnchiraBackgroundWebView(HentoidApp.Companion.getInstance(), Site.ANCHIRA);
            Timber.d(">> loading url %s", pageUrl);
            anchiraWv.loadUrl(pageUrl);
            //anchiraWv.loadUrl(pageUrl, () -> evaluateJs(anchiraWv, galleryInfo, imagesStr, done));
            Timber.i(">> loading wv");
        });

        return Collections.emptyList();
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageListImpl is overriden directly
        return null;
    }
}
