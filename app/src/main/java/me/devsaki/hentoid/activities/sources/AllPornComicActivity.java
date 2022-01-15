package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class AllPornComicActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "allporncomic.com";
    private static final String[] GALLERY_FILTER = {"allporncomic.com/porncomic/[%\\w\\-]+/$"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER + "/cdn", DOMAIN_FILTER + "/wp"};
    private static final String[] JS_CONTENT_BLACKLIST = {"var exoloader;", "popunder"};
    private static final String[] DIRTY_ELEMENTS = {"iframe"};


    Site getStartSite() {
        return Site.ALLPORNCOMIC;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(DIRTY_ELEMENTS);
        client.adBlocker.addJsUrlWhitelist(JS_WHITELIST);
        for (String s : JS_WHITELIST) client.adBlocker.addJsUrlPatternWhitelist(s); // TODO duplicate of above ?
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client;
    }
}
