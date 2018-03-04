package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {

    private boolean downloadFabPressed = false;
    private int historyIndex;

    private static int ordinalIndexOf(String str) {
        int i = 5;
        int pos = str.indexOf('/', 0);
        while (i-- > 0 && pos != -1) {
            pos = str.indexOf('/', pos + 1);
        }

        return pos;
    }

    Site getStartSite() {
        return Site.TSUMINO;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        TsuminoWebViewClient client = new TsuminoWebViewClient();
        client.restrictTo("tsumino.com");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @SuppressWarnings("UnusedParameters")
    @Override
    public void onDownloadFabClick(View view) {
        downloadFabPressed = true;
        historyIndex = getWebView().copyBackForwardList().getCurrentIndex();

        String newUrl = getWebView().getUrl().replace("Book/Info", "Read/View");
        final int index = ordinalIndexOf(newUrl);
        if (index > 0) newUrl = newUrl.substring(0, index);
        getWebView().loadUrl(newUrl);
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {
        final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (downloadFabPressed &&
                    !(url.contains("//www.tsumino.com/Read/View/") ||
                            url.contains("//www.tsumino.com/Read/Auth/") ||
                            url.contains("//www.tsumino.com/Read/AuthProcess"))) {
                downloadFabPressed = false;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            if (url.contains("//www.tsumino.com/Book/Info/")) {
                executeAsyncTask(new HtmlLoader(), url);
            } else if (downloadFabPressed && url.contains("//www.tsumino.com/Read/View/")) {
                downloadFabPressed = false;
                int currentIndex = getWebView().copyBackForwardList().getCurrentIndex();
                getWebView().goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("pop.js") || url.contains("/static/")) {
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
            if (url.contains("pop.js") || url.contains("/static/")) {
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
                processContent(TsuminoParser.parseContent(url));
            } catch (IOException e) {
                Timber.e(e, "Error parsing content.");
            }

            return null;
        }
    }
}
