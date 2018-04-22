package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by robb_w on 01/31/2018.
 * Implements Pururin source
 */
public class PururinActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.PURURIN;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        webView.setWebViewClient(new PururinWebViewClient(this, "//pururin.io/gallery/"));

        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class PururinWebViewClient extends CustomWebViewClient {
        PururinWebViewClient(BaseWebActivity activity, String filteredUrl) {
            super(activity, filteredUrl);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("pururin.io"));
            } catch (MalformedURLException e) {
                Timber.d("Malformed URL");
            }

            return false;
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            try {
                URL u = new URL(request.getUrl().toString());
                return !(u.getHost().endsWith("pururin.io"));
            } catch (MalformedURLException e) {
                Timber.d("Malformed URL");
            }

            return false;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            /*Workaround for cache miss when re-submitting data to search form*/
            view.loadUrl(view.getOriginalUrl());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("f.js") || isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("f.js") || isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
