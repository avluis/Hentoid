package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

/**
 * Created by Robb_w on 2018/04
 * Implements MangaPanda source
 */
public class PandaActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.PANDA;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        PandaWebViewClient client = new PandaWebViewClient(this, "mangapanda.com/[A-Za-z0-9\\-_]+/[0-9]+");
        client.restrictTo("mangapanda.com");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class PandaWebViewClient extends CustomWebViewClient {

        PandaWebViewClient(BaseWebActivity activity, String filteredUrl) {
            super(activity, filteredUrl);
        }
    }
}
