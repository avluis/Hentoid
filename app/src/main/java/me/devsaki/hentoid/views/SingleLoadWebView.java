package me.devsaki.hentoid.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

public class SingleLoadWebView extends WebView {

    SingleLoadWebViewClient client;

    public SingleLoadWebView(@NonNull final Context context, @NonNull final Site site) {
        super(context);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptThirdPartyCookies(this, true);

        WebSettings webSettings = getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        webSettings.setUserAgentString(site.getUserAgent());

        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);

        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true);

        client = new SingleLoadWebViewClient(site);
        setWebViewClient(client);
    }

    @Override
    public void loadUrl(@NonNull String url) {
        client.forceLoading();
        super.loadUrl(url);
    }

    public boolean isLoading() {
        return client.isLoading();
    }

    static class SingleLoadWebViewClient extends WebViewClient {

        private final Site site;
        private final AtomicBoolean isPageLoading = new AtomicBoolean(false);

        public SingleLoadWebViewClient(Site site) {
            this.site = site;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Timber.v(">>> onPageStarted %s", url);
            isPageLoading.set(true);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Timber.v(">>> onPageFinished %s", url);
            isPageLoading.set(false);
        }

        void forceLoading() {
            isPageLoading.set(true);
        }

        boolean isLoading() {
            return isPageLoading.get();
        }
    }
}
