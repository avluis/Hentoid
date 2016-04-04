package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.abstracts.BaseWebActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 */
public class NhentaiActivity extends BaseWebActivity {
    private static final String TAG = LogHelper.makeLogTag(NhentaiActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setSite(Site.NHENTAI);
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new NhentaiWebViewClient());
    }

    private WebResourceResponse getJSWebResourceResponseFromAsset() {
        String pathPrefix = getSite().getDescription().toLowerCase() + "/";
        String file = pathPrefix + "main_js.js";
        try {
            return getUtf8EncodedJSWebResourceResponse(getAssets().open(file));
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getDomainWebResourceResponseFromAsset() {
        String pathPrefix = getSite().getDescription().toLowerCase() + "/";
        String file = pathPrefix + "ads2";
        try {
            return getUtf8EncodedHtmlWebResourceResponse(getAssets().open(file));
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getCssWebResourceResponseFromAsset() {
        String pathPrefix = getSite().getDescription().toLowerCase() + "/";
        String file = pathPrefix + "main_style.css";
        try {
            return getUtf8EncodedCssWebResourceResponse(getAssets().open(file));
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getUtf8EncodedJSWebResourceResponse(InputStream open) {
        return new WebResourceResponse("text/js", "UTF-8", open);
    }

    private WebResourceResponse getUtf8EncodedHtmlWebResourceResponse(InputStream open) {
        return new WebResourceResponse("text/html", "UTF-8", open);
    }

    private WebResourceResponse getUtf8EncodedCssWebResourceResponse(InputStream open) {
        return new WebResourceResponse("text/css", "UTF-8", open);
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("main.82a43f8c3ce0.js")) {
                return getJSWebResourceResponseFromAsset();
            } else if (url.contains("ads.contentabc.com")) {
                return getDomainWebResourceResponseFromAsset();
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getCssWebResourceResponseFromAsset();
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            if (request.getUrl().toString().contains("main_js.js")) {
                return getJSWebResourceResponseFromAsset();
            } else if (request.getUrl().toString().contains("ads2.contentabc.com")) {
                return getDomainWebResourceResponseFromAsset();
            } else if (request.getUrl().toString().contains("//static.nhentai.net/css/")) {
                return getCssWebResourceResponseFromAsset();
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }

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

            if (url.contains("//nhentai.net/g/")) {
                AndroidHelper.executeAsyncTask(new LoaderJson(), url + "json");
            }
        }
    }

    private class LoaderJson extends AsyncTask<String, Integer, Content> {
        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                processContent(NhentaiParser.parseContent(HttpClientHelper.call(url)));
            } catch (Exception e) {
                LogHelper.e(TAG, "Error parsing JSON: " + url, e);
            }

            return null;
        }
    }
}