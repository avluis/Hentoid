package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class Hentai2ReadActivity extends BaseWebActivity {

    public static final String GALLERY_PATTERN = "//hentai2read.com/[\\w\\-]+/$";

    private static final String DOMAIN_FILTER = "hentai2read.com";
    private static final String[] GALLERY_FILTER = {GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "[0-9\\.]+/$"};
    private static final String[] REMOVABLE_ELEMENTS = {"div[data-refresh]"}; // iframe[src*=ads]
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};
    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder", "trackingurl", "exo_slider", "exojspop", "data-exo"};


    Site getStartSite() {
        return Site.HENTAI2READ;
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
