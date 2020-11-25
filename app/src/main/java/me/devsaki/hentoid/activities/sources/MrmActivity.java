package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class MrmActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "myreadingmanga.info";
    private static final String[] GALLERY_FILTER = {"myreadingmanga.info/[%\\w\\-]+/$"};
    private static final String[] DIRTY_ELEMENTS = {"center.imgtop"};

    Site getStartSite() {
        return Site.MRM;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
