package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.views.ObservableWebView;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "tsumino.com";
//    private static final String GALLERY_FILTER = "";  Multiple gallery filters ?

    private boolean downloadFabPressed = false;
    private int historyIndex;
    private static final String[] blockedContent = {"/static/"};


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
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new TsuminoWebViewClient(getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        addContentBlockFilter(blockedContent);

        return client;
    }

    @Override
    public void onDownloadFabClick(View view) {
        downloadFabPressed = true;
        historyIndex = webView.copyBackForwardList().getCurrentIndex();

        // TODO - just what is this hack ?!
        String newUrl = webView.getUrl().replace("Book/Info", "Read/View");
        final int index = ordinalIndexOf(newUrl);
        if (index > 0) newUrl = newUrl.substring(0, index);
        webView.loadUrl(newUrl);
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {

        TsuminoWebViewClient(Site startSite, ResultListener<Content> listener) {
            super(startSite, listener);
        }

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
                executeAsyncTask(new HtmlLoader(startSite, listener), url);
            } else if (downloadFabPressed && url.contains("//www.tsumino.com/Read/View/")) {
                downloadFabPressed = false;
                int currentIndex = webView.copyBackForwardList().getCurrentIndex();
                webView.goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }

    }
}
