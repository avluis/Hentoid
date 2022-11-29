package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class HdPornComicsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hdporncomics.com";
    private static final String[] GALLERY_FILTER = {"hdporncomics.com/[\\w\\-]+/$"};

    Site getStartSite() {
        return Site.HDPORNCOMICS;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
