package me.devsaki.hentoid.activities.sources;

import android.graphics.Bitmap;
import android.webkit.WebView;

import me.devsaki.hentoid.enums.Site;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "tsumino.com";
    private static final String[] GALLERY_FILTER = {"//www.tsumino.com/entry/"};
    private static final String[] blockedContent = {"/static/"};
    private static final String[] DIRTY_ELEMENTS = {".ads-area"};
    private boolean downloadFabPressed = false;
    private int historyIndex;

    Site getStartSite() {
        return Site.TSUMINO;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addDirtyElements(DIRTY_ELEMENTS);
        addContentBlockFilter(blockedContent);
        CustomWebViewClient client = new TsuminoWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);

        return client;
    }

    @Override
    public void onActionFabClick() {
        if (ActionMode.DOWNLOAD == actionButtonMode) {
            downloadFabPressed = true;
            historyIndex = webView.copyBackForwardList().getCurrentIndex();

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            String newUrl = webView.getUrl().replace("entry", "Read/Index");
            webView.loadUrl(newUrl);
        } else {
            super.onActionFabClick();
        }
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {

        TsuminoWebViewClient(String[] galleryFilter, WebContentListener listener) {
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
                processDownload(false);
            }
        }
    }
}
