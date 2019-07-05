package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;

public class FakkuActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "fakku.net";
    private static final String GALLERY_FILTER = "fakku.net/hentai/";

    Site getStartSite() {
        return Site.FAKKU2;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new FakkuViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class FakkuViewClient extends CustomWebViewClient {

        FakkuViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }
    }
}
