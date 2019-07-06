package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class MusesActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "8muses.com";
    private static final String GALLERY_FILTER = "//www.8muses.com/comics/album/";

    Site getStartSite() {
        return Site.MUSES;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
