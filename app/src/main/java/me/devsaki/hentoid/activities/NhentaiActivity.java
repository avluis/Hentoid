package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
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
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 */
public class NhentaiActivity extends BaseWebActivity {
    private static final String TAG = LogHelper.makeLogTag(NhentaiActivity.class);

    @Override
    void setSite(Site site) {
        super.setSite(Site.NHENTAI);
    }

    @Override
    void setWebView(ObservableWebView webView) {
        webView.setWebViewClient(new NhentaiWebViewClient());

        super.setWebView(webView);
    }

    private WebResourceResponse getJSWebResourceResponseFromCache() {
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + "main_js.js";
        try {
            File asset = new File(getExternalCacheDir() + "/" + file);
            LogHelper.d(TAG, "File: " + asset);
            FileInputStream stream = new FileInputStream(asset);
            return Helper.getUtf8EncodedWebResourceResponse(stream, 1);
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getDomainWebResourceResponseFromCache() {
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + "ads2";
        try {
            File asset = new File(getExternalCacheDir() + "/" + file);
            LogHelper.d(TAG, "File: " + asset);
            FileInputStream stream = new FileInputStream(asset);
            return Helper.getUtf8EncodedWebResourceResponse(stream, 0);
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getCssWebResourceResponseFromCache() {
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + "main_style.css";
        try {
            File asset = new File(getExternalCacheDir() + "/" + file);
            LogHelper.d(TAG, "File: " + asset);
            FileInputStream stream = new FileInputStream(asset);
            return Helper.getUtf8EncodedWebResourceResponse(stream, 2);
        } catch (IOException e) {
            return null;
        }
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("nhentai.net"));
            } catch (MalformedURLException e) {
                LogHelper.d(TAG, "Malformed URL");
            }

            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("nhentai.net/g/")) {
                String newURL = url.replace("/g", "/api/gallery");
                newURL = newURL.substring(0, newURL.length() - 1);
                Helper.executeAsyncTask(new JsonLoader(), newURL);
            }
        }

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("//static.nhentai.net/js/")) {
                return getJSWebResourceResponseFromCache();
            } else if (url.contains("ads.contentabc.com")) {
                return getDomainWebResourceResponseFromCache();
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getCssWebResourceResponseFromCache();
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            if (request.getUrl().toString().contains("//static.nhentai.net/js/")) {
                return getJSWebResourceResponseFromCache();
            } else if (request.getUrl().toString().contains("ads2.contentabc.com")) {
                return getDomainWebResourceResponseFromCache();
            } else if (request.getUrl().toString().contains("//static.nhentai.net/css/")) {
                return getCssWebResourceResponseFromCache();
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }

    private class JsonLoader extends AsyncTask<String, Integer, Content> {
        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                processContent(NhentaiParser.parseContent(HttpClientHelper.call(url)));
            } catch (Exception e) {
                LogHelper.e(TAG, "Error parsing JSON: " + url + "\n", e);
            }

            return null;
        }
    }
}
