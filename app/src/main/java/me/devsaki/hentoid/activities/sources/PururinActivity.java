package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PururinActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pururin.to";
    private static final String[] GALLERY_FILTER = {"//pururin.to/gallery/"};

    Site getStartSite() {
        return Site.PURURIN;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
