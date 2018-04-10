package me.devsaki.hentoid.activities.websites;

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

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.PururinParser;
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
        webView.setWebViewClient(new PururinWebViewClient());

        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(), extra);
    }

    private class PururinWebViewClient extends CustomWebViewClient {
        final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

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
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("//pururin.io/gallery/")) {
                executeAsyncTask(new HtmlLoader(), url);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("ads.js") || url.contains("f.js") || url.contains("pop.js") ||
                    url.contains("ads.php") || url.contains("syndication.exoclick.com")) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com") || url.contains("juicyads.com")) {
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
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com") || url.contains("juicyads.com")) {
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
                ContentParser parser = ContentParserFactory.getInstance().getParser(Site.PURURIN);
                processContent(parser.parseContent(url));
            } catch (Exception e) {
                Timber.e(e, "Error parsing content.");
                runOnUiThread(() -> Helper.toast(HentoidApp.getAppContext(), R.string.web_unparsable));
            }

            return null;
        }
    }
}
