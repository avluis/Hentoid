package me.devsaki.hentoid.parsers.images;

import android.os.Handler;
import android.os.Looper;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.devsaki.hentoid.activities.sources.WebResultConsumer;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.views.AnchiraBackgroundWebView;
import timber.log.Timber;

/**
 * Handles parsing of content from anchira.to
 */
public class AnchiraParser extends BaseImageListParser implements WebResultConsumer {

    private final AtomicInteger resultCode = new AtomicInteger(-1);
    private final AtomicReference<Content> resultContent = new AtomicReference<>();
    private AnchiraBackgroundWebView anchiraWv = null;

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

    public Content parseContentWithWebview(@NonNull String url) throws Exception {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            anchiraWv = new AnchiraBackgroundWebView(HentoidApp.Companion.getInstance(), this, Site.ANCHIRA);
            Timber.d(">> loading url %s", url);
            anchiraWv.loadUrl(url);
            Timber.i(">> loading wv");
        });

        var remainingIterations = 30; // Timeout
        while (-1 == resultCode.get() && remainingIterations-- > 0 && !processHalted.get())
            Helper.pause(500);

        if (processHalted.get())
            throw new EmptyResultException("Unable to detect content (empty result)");

        synchronized (resultCode) {
            int res = resultCode.get();
            if (0 == res) {
                Content c = resultContent.get();
                if (c != null) return c;
            } else if (-1 == res) {
                throw new ParseException("Parsing failed to start");
            } else if (2 == res) {
                throw new ParseException("Parsing has failed unexpectedly");
            }
            throw new EmptyResultException("Parsing hasn't found any content");
        }
    }

    public List<ImageFile> parseImageListWithWebview(@NonNull Content onlineContent) throws Exception {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            anchiraWv = new AnchiraBackgroundWebView(HentoidApp.Companion.getInstance(), this, Site.ANCHIRA);
            String pageUrl = onlineContent.getReaderUrl();
            Timber.d(">> loading url %s", pageUrl);
            anchiraWv.loadUrl(pageUrl);
            Timber.i(">> loading wv");
        });

        var remainingIterations = 30; // Timeout
        while (-1 == resultCode.get() && remainingIterations-- > 0 && !processHalted.get())
            Helper.pause(500);

        if (processHalted.get())
            throw new EmptyResultException("Unable to detect pages (empty result)");

        synchronized (resultCode) {
            int res = resultCode.get();
            if (0 == res) {
                Content c = resultContent.get();
                if (c != null) {
                    String imgUrl = c.getCoverImageUrl();
                    HttpHelper.UriParts parts = new HttpHelper.UriParts(imgUrl);
                    String fileName = parts.getFileNameNoExt();
                    int length = fileName.length();

                    List<String> urls = new ArrayList<>();
                    for (int i = 1; i <= onlineContent.getQtyPages(); i++) {
                        parts.setFileNameNoExt(StringHelper.formatIntAsStr(i, length));
                        parts.setExtension((1 == i) ? "jpg" : "png"); // Try to minimize failed requests
                        urls.add(parts.toUri());
                    }

                    return ParseHelper.urlsToImageFiles(urls, onlineContent.getCoverImageUrl(), StatusContent.SAVED);
                }
            } else if (-1 == res) {
                throw new ParseException("Parsing failed to start");
            } else if (2 == res) {
                throw new ParseException("Parsing has failed unexpectedly");
            }
            throw new EmptyResultException("Parsing hasn't found any page");
        }
    }

    public void destroy() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (anchiraWv != null) anchiraWv.destroy();
            anchiraWv = null;
        });
    }

    @Override
    public String getAltUrl(@NonNull String url) {
        HttpHelper.UriParts parts = new HttpHelper.UriParts(url);
        String ext = parts.getExtension();
        String altExt = (ext.equalsIgnoreCase("jpg")) ? "png" : "jpg";
        parts.setExtension(altExt);
        return parts.toUri();
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        // We won't use that as parseImageListImpl is overriden directly
        return null;
    }

    @Override
    public void onContentReady(@NonNull Content result, boolean quickDownload) {
        resultContent.set(result);
        resultCode.set(0);
    }

    @Override
    public void onNoResult() {
        resultCode.set(1);
    }

    @Override
    public void onResultFailed() {
        resultCode.set(2);
    }
}
