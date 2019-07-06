package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class NexusActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentainexus.com";
    private static final String GALLERY_FILTER = "//hentainexus.com/view/";

    Site getStartSite() {
        return Site.NEXUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
