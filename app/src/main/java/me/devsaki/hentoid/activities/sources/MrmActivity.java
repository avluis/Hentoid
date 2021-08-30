package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class MrmActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "myreadingmanga.info";
    private static final String[] GALLERY_FILTER = {"myreadingmanga.info/[%\\w\\-]+/$"};
    private static final String[] DIRTY_ELEMENTS = {"center.imgtop","a[rel^='nofollow noopener']"};

    Site getStartSite() {
        return Site.MRM;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
