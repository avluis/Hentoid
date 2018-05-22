package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

public class PururinActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.PURURIN;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        PururinViewClient client = new PururinViewClient(this, "//pururin.io/gallery/");
        client.restrictTo("pururin.io");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class PururinViewClient extends CustomWebViewClient {

        PururinViewClient(BaseWebActivity activity, String filteredUrl) {
            super(activity, filteredUrl);
        }
    }
}
