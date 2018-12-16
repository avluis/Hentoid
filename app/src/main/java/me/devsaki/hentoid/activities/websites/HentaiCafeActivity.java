package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentai.cafe";
    private static final String GALLERY_FILTER = "//hentai.cafe/";

    Site getStartSite() {
        return Site.HENTAICAFE;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new HentaiCafeWebViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

/*
    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }
    */

    private class HentaiCafeWebViewClient extends CustomWebViewClient {

        HentaiCafeWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, startSite, listener);
        }
    }
}
