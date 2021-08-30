package me.devsaki.hentoid.views;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.annimon.stream.function.Consumer;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class CloudflareWebView extends WebView {

    private Consumer<String> onCookiesCallback;

    public CloudflareWebView(@NonNull final Context context, @NonNull final Site site) {
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

        CloudflareWebViewClient client = new CloudflareWebViewClient(site, onCookiesCallback);
        setWebViewClient(client);
    }

    public void setonCloudflareCookies(Consumer<String> callback) {
        onCookiesCallback = callback;
    }

    static class CloudflareWebViewClient extends WebViewClient {

        private final Site site;
        private Consumer<String> onCookiesCallback;

        public CloudflareWebViewClient(Site site, Consumer<String> callback) {
            this.site = site;
            this.onCookiesCallback = callback;
        }

        /**
         * Note : this method is called by a non-UI thread
         */
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            // Only detect cookies on HTML pages
            if (HttpHelper.getExtensionFromUri(url).isEmpty()) {
                Timber.v("+shouldInterceptRequest %s", url);
/*
                List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), url);
                try {
                    // Query resource here, using OkHttp
                    Response response = HttpHelper.getOnlineResource(url, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());

                    // Scram if the response is a redirection or an error
                    if (response.code() >= 300) return null;

                    // Scram if the response is something else than html
                    String rawContentType = response.header(HEADER_CONTENT_TYPE, "");
                    if (null == rawContentType) return null;

                    Pair<String, String> contentType = HttpHelper.cleanContentType(rawContentType);
                    if (!contentType.first.isEmpty() && !contentType.first.equals("text/html"))
                        return null;

                    // Scram if the response is empty
                    ResponseBody body = response.body();
                    if (null == body) throw new IOException("Empty body");

                    InputStream browserStream = body.byteStream();
                    // Convert OkHttp response to the expected format
                    WebResourceResponse result = HttpHelper.okHttpResponseToWebkitResponse(response, browserStream);

                    // Manually set cookie if present in response header (has to be set manually because we're using OkHttp right now, not the webview)
                    if (result.getResponseHeaders().containsKey("set-cookie") || result.getResponseHeaders().containsKey("Set-Cookie")) {
                        String cookiesStr = result.getResponseHeaders().get("set-cookie");
                        if (null == cookiesStr)
                            cookiesStr = result.getResponseHeaders().get("Set-Cookie");
                        if (cookiesStr != null) {
                            // Set-cookie might contain multiple cookies to set separated by a line feed (see HttpHelper.getValuesSeparatorFromHttpHeader)
                            String[] cookieParts = cookiesStr.split("\n");
                            for (String cookie : cookieParts)
                                if (!cookie.isEmpty())
                                    HttpHelper.setCookies(url, cookie);
                            // Warn client about cloudflare cookies being set
                            if (cookiesStr.contains("cf_clearance") || cookiesStr.contains("__cf"))
                                onCookiesCallback.accept(cookiesStr);
                        }
                    }
                    return result;
                } catch (MalformedURLException e) {
                    Timber.e(e, "Malformed URL : %s", url);
                } catch (IOException e) {
                    Timber.e(e);
                }
 */
            } else {
                Timber.v("-shouldInterceptRequest %s", url);
            }
            return super.shouldInterceptRequest(view, request);
        }
    }
}
