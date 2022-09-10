package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class MultpornActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "multporn.net";
    private static final String[] GALLERY_FILTER = {"multporn.net/node/[0-9]+$", "multporn.net/(hentai_manga|hentai|comics|pictures|rule_6|gay_porn_comics|GIF)/[\\w%_\\-]+$"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};
    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder"};
    private static final String[] AD_ELEMENTS = {"iframe", ".c-ads"};


    Site getStartSite() {
        return Site.MULTPORN;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(AD_ELEMENTS);
        client.addJavascriptBlacklist(JS_CONTENT_BLACKLIST);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);
        //for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client;
    }
}
