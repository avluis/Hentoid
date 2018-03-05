package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 */
public class NhentaiActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.NHENTAI;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        NhentaiWebViewClient client = new NhentaiWebViewClient();
        client.restrictTo("nhentai.net");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {
        final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("nhentai.net/g/")) {
                String newURL = url.replace("/g", "/api/gallery");
                newURL = newURL.substring(0, newURL.length() - 1);
                executeAsyncTask(new JsonLoader(), newURL);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_style.css", TYPE.CSS);
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
                return getWebResourceResponseFromAsset(getStartSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_style.css", TYPE.CSS);
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
                Timber.e(e, "Error parsing content.");
            }

            return null;
        }
    }
}
