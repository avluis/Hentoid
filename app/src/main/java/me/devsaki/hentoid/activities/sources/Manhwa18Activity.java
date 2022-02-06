package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class Manhwa18Activity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "manhwa18.com";
    private static final String[] GALLERY_FILTER = {"//manhwa18.com/manga/", "//manhwa18.com/manga/[\\w\\-]+/chap"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};
//    private static final String[] DIRTY_ELEMENTS = { };

    Site getStartSite() {
        return Site.MANHWA18;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);
//        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
