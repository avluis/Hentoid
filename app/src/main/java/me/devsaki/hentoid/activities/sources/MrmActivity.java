package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class MrmActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "myreadingmanga.info";
    private static final String[] GALLERY_FILTER = {"myreadingmanga.info/[%\\w\\-]+/$"};

    Site getStartSite() {
        return Site.MRM;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        MrmWebClient client = new MrmWebClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class MrmWebClient extends CustomWebViewClient {

        MrmWebClient(String[] filter, WebContentListener listener) {
            super(filter, listener);
        }
    }
}
