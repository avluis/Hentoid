package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class KskActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "ksk.moe";
    private static final String[] GALLERY_FILTER = {"ksk.moe/view/[0-9]+/[\\w\\-]+$"};
    private static final String[] REMOVABLE_ELEMENTS = {".bwrp"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};
    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder", "trackingurl"};

    Site getStartSite() {
        return Site.KSK;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(REMOVABLE_ELEMENTS);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client;
    }
}
