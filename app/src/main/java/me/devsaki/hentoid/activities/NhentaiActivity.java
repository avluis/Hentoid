package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

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

    private class NhentaiWebViewClient extends CustomWebViewClient {
        final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

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
                executeAsyncTask(new JsonLoader(), newURL);
            }
        }

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getSite(), "main_style.css", TYPE.CSS);
            } else if (url.contains("ads.contentabc.com")) {
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
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getSite(), "main_style.css", TYPE.CSS);
            } else if (url.contains("ads2.contentabc.com")) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
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
