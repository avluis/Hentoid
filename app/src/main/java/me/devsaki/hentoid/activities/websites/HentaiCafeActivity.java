package me.devsaki.hentoid.activities.websites;

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
        HentaiCafeWebViewClient client = new HentaiCafeWebViewClient(this, "//hentai.cafe/");
        client.restrictTo("hentai.cafe");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class HentaiCafeWebViewClient extends CustomWebViewClient {

        HentaiCafeWebViewClient(BaseWebActivity activity, String filteredUrl) {
            super(activity, filteredUrl);
        }
    }
}
