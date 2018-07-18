package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.views.ObservableWebView;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {

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
    void setWebView(ObservableWebView webView) {
        TsuminoWebViewClient client = new TsuminoWebViewClient(this);
        client.restrictTo("tsumino.com");
        addContentBlockFilter(blockedContent);

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

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
        TsuminoWebViewClient(BaseWebActivity activity) {
            super(activity);
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
                executeAsyncTask(new HtmlLoader(activity), url);
            } else if (downloadFabPressed && url.contains("//www.tsumino.com/Read/View/")) {
                downloadFabPressed = false;
                int currentIndex = getWebView().copyBackForwardList().getCurrentIndex();
                getWebView().goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (isUrlForbidden(url)) {
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
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
