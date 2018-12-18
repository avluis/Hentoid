package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.util.Helper;

public class PururinActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pururin.io";
    private static final String GALLERY_FILTER = "//pururin.io/gallery/";

    Site getStartSite() {
        return Site.PURURIN;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new PururinViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class PururinViewClient extends CustomWebViewClient {

        PururinViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, startSite, listener);
        }

        @Override
        protected void onGalleryFind(String url) {
            Helper.executeAsyncTask(new HtmlLoader(startSite, listener), url);
        }
    }
}
