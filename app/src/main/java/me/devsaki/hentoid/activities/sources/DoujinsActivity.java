package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class DoujinsActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "doujins.com";
    private static final String[] GALLERY_FILTER = {"//doujins.com/[\\w\\-]+/[\\w\\-]+-[0-9]+"};

    Site getStartSite() {
        return Site.DOUJINS;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
