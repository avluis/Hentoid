package me.devsaki.hentoid.activities.websites;

import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebView;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.TsuminoServer;
import timber.log.Timber;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "tsumino.com";
    private static final String GALLERY_FILTER = "//www.tsumino.com/Book/Info/";

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
        CustomWebViewClient client = new TsuminoWebViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        addContentBlockFilter(blockedContent);

        return client;
    }

    @Override
    public void onDownloadFabClick(View view) {
        downloadFabPressed = true;
        historyIndex = webView.copyBackForwardList().getCurrentIndex();

        // Hack to reach the first gallery page to initiate download, and go back to the book page
        String newUrl = webView.getUrl().replace("Book/Info", "Read/View");
        final int index = ordinalIndexOf(newUrl);
        if (index > 0) newUrl = newUrl.substring(0, index);
        webView.loadUrl(newUrl);
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {

        TsuminoWebViewClient(String galleryFilter, Site startSite, ResultListener<Content> listener) {
            super(galleryFilter, startSite, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            // Tsumino books can be called through two different URLs : "book ID" and "book ID/book-name"
            // -> need to get the book ID only
            compositeDisposable.add(TsuminoServer.API.getGalleryMetadata(galleryUrlParts[5])
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                listener.onResultFailed("");
                            })
            );
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            // Hack to reach the first gallery page to initiate download, and go back to the book page
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

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            if (downloadFabPressed && url.contains("//www.tsumino.com/Read/View/")) {
                downloadFabPressed = false;
                int currentIndex = webView.copyBackForwardList().getCurrentIndex();
                webView.goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }

    }
}
