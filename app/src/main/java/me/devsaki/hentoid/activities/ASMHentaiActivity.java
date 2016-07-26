package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ASMHentaiParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

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

    private WebResourceResponse getJSWebResourceResponseFromAsset(String script) {
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + script;
        try {
            File asset = new File(getExternalCacheDir() + "/" + file);
            FileInputStream stream = new FileInputStream(asset);
            return Helper.getUtf8EncodedWebResourceResponse(stream, 1);
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getDomainWebResourceResponseFromCache(@Nullable String page) {
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";
        String file;
        if (page != null) {
            file = pathPrefix + page;
        } else {
            file = pathPrefix + "ads.html";
        }
        try {
            File asset = new File(getExternalCacheDir() + "/" + file);
            FileInputStream stream = new FileInputStream(asset);
            return Helper.getUtf8EncodedWebResourceResponse(stream, 0);
        } catch (IOException e) {
            return null;
        }
    }

    private class ASMHentaiWebViewClient extends CustomWebViewClient {

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
                Helper.executeAsyncTask(new HtmlLoader(), url);
            }
        }

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("ads.js") || url.contains("f.js") || url.contains("pop.js") ||
                    url.contains("ads.php") || url.contains("syndication.exoclick.com")) {
                return getJSWebResourceResponseFromAsset("no.js");
            } else if (url.contains("main.js")) {
                return getJSWebResourceResponseFromAsset("main.js");
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com")) {
                return getDomainWebResourceResponseFromCache(null);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            if (request.getUrl().toString().contains("ads.js") ||
                    request.getUrl().toString().contains("f.js") ||
                    request.getUrl().toString().contains("pop.js") ||
                    request.getUrl().toString().contains("syndication.exoclick.com")) {
                return getJSWebResourceResponseFromAsset("no.js");
            } else if (request.getUrl().toString().contains("main.js")) {
                return getJSWebResourceResponseFromAsset("main.js");
            } else if (request.getUrl().toString().contains("exoclick.com") ||
                    request.getUrl().toString().contains("juicyadultads.com")) {
                return getDomainWebResourceResponseFromCache(null);
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
                e.printStackTrace();
            }

            return null;
        }
    }
}
