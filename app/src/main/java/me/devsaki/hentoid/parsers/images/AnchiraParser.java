package me.devsaki.hentoid.parsers.images;

import android.os.Handler;
import android.os.Looper;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.devsaki.hentoid.activities.sources.WebResultConsumer;
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
public class AnchiraParser extends BaseImageListParser implements WebResultConsumer {

    AtomicInteger result = new AtomicInteger(-1);

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
            AnchiraBackgroundWebView anchiraWv = new AnchiraBackgroundWebView(HentoidApp.Companion.getInstance(), this, Site.ANCHIRA);
            Timber.d(">> loading url %s", pageUrl);
            anchiraWv.loadUrl(pageUrl);
            Timber.i(">> loading wv");
        });

        while (-1 == result.get()) Helper.pause(500);
        Timber.i(">> result obtained : %d", result.get());

        return Collections.emptyList();
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageListImpl is overriden directly
        return null;
    }

    @Override
    public void onResultReady(@NonNull Content results, boolean quickDownload) {
        Timber.i(results.getTitle());
        result.set(0);
    }

    @Override
    public void onNoResult() {
        result.set(1);
    }

    @Override
    public void onResultFailed() {
        result.set(2);
    }
}
