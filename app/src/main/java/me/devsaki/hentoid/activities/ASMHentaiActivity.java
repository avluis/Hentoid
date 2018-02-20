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

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ASMHentaiParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by avluis on 07/21/2016.
 * Implements ASMHentai source
 */
public class ASMHentaiActivity extends BaseWebActivity {

    @Override
    void setSite(Site site) {
        super.setSite(Site.ASMHENTAI);
    }

    @Override
    void setWebView(ObservableWebView webView) {
        ASMHentaiWebViewClient client = new ASMHentaiWebViewClient();
        client.restrictTo("asmhentai.com");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(), extra);
    }

    private class ASMHentaiWebViewClient extends CustomWebViewClient {
        final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            /*Workaround for cache miss when re-submitting data to search form*/
            view.loadUrl(view.getOriginalUrl());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("//asmhentai.com/g/") || url.contains("//comics.asmhentai.com/g/")) {
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
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com")|| url.contains("exosrv.com")|| url.contains("hentaigold.net")) {
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
            } else if (url.contains("exoclick.com") || url.contains("juicyadultads.com")|| url.contains("exosrv.com")|| url.contains("hentaigold.net")) {
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
                Timber.e(e, "Error parsing content.");
            }

            return null;
        }
    }
}
