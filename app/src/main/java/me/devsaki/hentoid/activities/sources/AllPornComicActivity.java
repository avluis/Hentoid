package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class AllPornComicActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "allporncomic.com";
    private static final String[] GALLERY_FILTER = {"allporncomic.com/porncomic/[%\\w\\-]+/$"};
//    private static final String[] DIRTY_ELEMENTS = {"center.imgtop"};

    Site getStartSite() {
        return Site.ALLPORNCOMIC;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
//        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
