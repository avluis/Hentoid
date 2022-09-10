package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class HbrowseActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hbrowse.com";
    private static final String[] GALLERY_FILTER = {"hbrowse.com/[0-9]+/c[0-9]+[/[0-9]+]{0,1}$"};

    Site getStartSite() {
        return Site.HBROWSE;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
