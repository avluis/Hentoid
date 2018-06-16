package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by Shiro on 1/20/2016.
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.HITOMI;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        HitomiWebViewClient client = new HitomiWebViewClient(this, "//hitomi.la/galleries/");
        client.restrictTo("hitomi.la");

        webView.setWebViewClient(client);

        boolean bWebViewOverview = Preferences.getWebViewOverview();
        int webViewInitialZoom = Preferences.getWebViewInitialZoom();

        if (bWebViewOverview) {
            webView.getSettings().setLoadWithOverviewMode(false);
            webView.setInitialScale(webViewInitialZoom);
            Timber.d("WebView Initial Scale: %s%%", webViewInitialZoom);
        } else {
            webView.setInitialScale(Preferences.Default.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT);
            webView.getSettings().setLoadWithOverviewMode(true);
        }

        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class HitomiWebViewClient extends CustomWebViewClient {
        HitomiWebViewClient(BaseWebActivity activity, String filteredUrl) {
            super(activity, filteredUrl);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("hitomi.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "hitomi.js", TYPE.JS);
            } else if (url.contains("hitomi-horizontal.js") || url.contains("hitomi-vertical.js") || isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("hitomi.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "hitomi.js", TYPE.JS);
            } else if (url.contains("hitomi-horizontal.js") || url.contains("hitomi-vertical.js") || isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
