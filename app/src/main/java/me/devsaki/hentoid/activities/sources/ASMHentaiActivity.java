package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

/**
 * Implements ASMHentai source
 */
public class ASMHentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "asmhentai.com";
    private static final String[] GALLERY_FILTER = {"asmhentai.com/g/"};
    private static final String[] REMOVABLE_ELEMENTS = {".atop"};
    private static final String[] blockedContent = {"f.js"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};
    private static final String[] JS_CONTENT_BLACKLIST = {"data-ad", "exoloader", "popunder", "close ad"};

    Site getStartSite() {
        return Site.ASMHENTAI;
    }


    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(REMOVABLE_ELEMENTS);
        client.adBlocker.addToUrlBlacklist(blockedContent);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client;
    }
}
