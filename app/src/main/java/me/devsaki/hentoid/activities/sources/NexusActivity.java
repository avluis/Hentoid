package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class NexusActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentainexus.com";
    private static final String[] DIRTY_ELEMENTS = {".unit-main", ".unit-dt-blk", ".unit-mobblk"};
    private static final String[] GALLERY_FILTER = {"//hentainexus.com/view/"};

    Site getStartSite() {
        return Site.NEXUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
