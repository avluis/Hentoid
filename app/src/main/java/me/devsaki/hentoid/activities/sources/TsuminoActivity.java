package me.devsaki.hentoid.activities.sources;

import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebView;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "tsumino.com";
    private static final String GALLERY_FILTER = "//www.tsumino.com/entry/";

    private boolean downloadFabPressed = false;
    private int historyIndex;
    private static final String[] blockedContent = {"/static/"};


    Site getStartSite() {
        return Site.TSUMINO;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new TsuminoWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        addContentBlockFilter(blockedContent);

        return client;
    }

    @Override
    public void onActionFabClick(View view) {
        if (MODE_DL == fabActionMode) {
            downloadFabPressed = true;
            historyIndex = webView.copyBackForwardList().getCurrentIndex();

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            String newUrl = webView.getUrl().replace("entry", "Read/Index");
            webView.loadUrl(newUrl);
        } else {
            super.onActionFabClick(view);
        }
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {

        TsuminoWebViewClient(String galleryFilter, ResultListener<Content> listener) {
            super(galleryFilter, listener);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            if (downloadFabPressed &&
                    !(url.contains("//www.tsumino.com/Read/Index/") ||
                            url.contains("//www.tsumino.com/Read/Auth/") ||
                            url.contains("//www.tsumino.com/Read/AuthProcess"))) {
                downloadFabPressed = false;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            if (downloadFabPressed && url.contains("//www.tsumino.com/Read/Index/")) {
                downloadFabPressed = false;
                int currentIndex = webView.copyBackForwardList().getCurrentIndex();
                webView.goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }

    }
}
