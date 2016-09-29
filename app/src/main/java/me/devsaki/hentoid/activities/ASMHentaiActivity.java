package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ASMHentaiParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by avluis on 07/21/2016.
 * Implements ASMHentai source
 */
public class ASMHentaiActivity extends BaseWebActivity {
    private final static String TAG = LogHelper.makeLogTag(ASMHentaiActivity.class);

    @Override
    void setSite(Site site) {
        super.setSite(Site.ASMHENTAI);
    }

    @Override
    void setWebView(ObservableWebView webView) {
        webView.setWebViewClient(new ASMHentaiWebViewClient());

        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        LogHelper.d(TAG, extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(), extra);
    }

    private class ASMHentaiWebViewClient extends CustomWebViewClient {
        final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

        @SuppressWarnings("deprecation") // From API 24 we should use another overload
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("asmhentai.com"));
            } catch (MalformedURLException e) {
                LogHelper.d(TAG, "Malformed URL");
            }

            return false;
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            try {
                URL u = new URL(request.getUrl().toString());
                return !(u.getHost().endsWith("asmhentai.com"));
            } catch (MalformedURLException e) {
                LogHelper.d(TAG, "Malformed URL");
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
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("//asmhentai.com/g/")) {
                executeAsyncTask(new HtmlLoader(), url);
            }
        }

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("ads.js") || url.contains("f.js") || url.contains("pop.js") ||
                    url.contains("ads.php") || url.contains("syndication.exoclick.com")) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getSite(), "main.js", TYPE.JS);
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com")) {
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
            if (url.contains("ads.js") || url.contains("f.js") || url.contains("pop.js") ||
                    url.contains("syndication.exoclick.com")) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getSite(), "main.js", TYPE.JS);
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com")) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }

    private class HtmlLoader extends AsyncTask<String, Integer, Content> {
        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                processContent(ASMHentaiParser.parseContent(url));
            } catch (IOException e) {
                LogHelper.e(TAG, e, "Error parsing content.");
            }

            return null;
        }
    }
}
