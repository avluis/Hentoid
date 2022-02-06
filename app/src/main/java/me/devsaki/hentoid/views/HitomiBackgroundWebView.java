package me.devsaki.hentoid.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class HitomiBackgroundWebView extends WebView {

    SingleLoadWebViewClient client;

    public HitomiBackgroundWebView(@NonNull final Context context, @NonNull final Site site) {
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

    public void loadUrl(@NonNull String url, Runnable onLoaded) {
        client.startLoad(url, onLoaded);
        super.loadUrl(url);
    }

    static class SingleLoadWebViewClient extends WebViewClient {

        private Site site;
        private String targetUrl;
        private Runnable onLoaded;
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
            if (onLoaded != null && targetUrl.equalsIgnoreCase(url)) onLoaded.run();
        }

        void startLoad(String url, Runnable onLoaded) {
            isPageLoading.set(true);
            this.targetUrl = url;
            this.onLoaded = onLoaded;
        }

        // Disable window width checks when the Webview in run outside of the UI
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("gg.js")) {
                List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), url);
                try {
                    // Query resource here, using OkHttp
                    Response response = HttpHelper.getOnlineResource(url, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
                    ResponseBody body = response.body();
                    if (null == body) throw new IOException("Empty body");
                    if (response.code() < 300) {
                        String jsFile = body.source().readString(StandardCharsets.UTF_8);
                        jsFile = jsFile.replaceAll("\\{[\\s]*return[\\s]+[0-9]+;[\\s]*\\}", "{return o;}");
                        return HttpHelper.okHttpResponseToWebkitResponse(response, new ByteArrayInputStream(jsFile.getBytes(StandardCharsets.UTF_8)));
                    }
                } catch (IOException e) {
                    Timber.w(e);
                }
            }
            return sendRequest(request);
        }

        // TODO optimize, factorize
        WebResourceResponse sendRequest(@NonNull WebResourceRequest request) {
            if (Preferences.getDnsOverHttps() > -1) {
                // Query resource using OkHttp
                String urlStr = request.getUrl().toString();
                List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), urlStr);
                try {
                    Response response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());

                    // Scram if the response is a redirection or an error
                    if (response.code() >= 300) return null;

                    ResponseBody body = response.body();
                    if (null == body) throw new IOException("Empty body");
                    return HttpHelper.okHttpResponseToWebkitResponse(response, body.byteStream());
                } catch (IOException e) {
                    Timber.i(e);
                }
            }
            return null;
        }
    }
}
