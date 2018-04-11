package me.devsaki.hentoid.activities.websites;

import android.graphics.Bitmap;
import android.webkit.WebView;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.HENTAICAFE;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        HentaiCafeWebViewClient client = new HentaiCafeWebViewClient();
        client.restrictTo("hentai.cafe");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(), extra);
    }

    private class HentaiCafeWebViewClient extends CustomWebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("//hentai.cafe/")) {
                executeAsyncTask(new HtmlLoader(), url);
            }
        }
    }
}
