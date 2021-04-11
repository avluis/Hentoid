package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class Hentai2ReadActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentai2read.com";
    private static final String[] GALLERY_FILTER = {"//hentai2read.com/[\\w\\-]+/$"};
    private static final String[] DIRTY_ELEMENTS = {"div[data-refresh]"}; // iframe[src*=ads]

    Site getStartSite() {
        return Site.HENTAI2READ;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
