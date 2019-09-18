package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PururinActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pururin.io";
    private static final String GALLERY_FILTER = "//pururin.io/gallery/";

    Site getStartSite() {
        return Site.PURURIN;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
