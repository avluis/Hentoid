package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class ToonilyActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "toonily.com";
    private static final String[] GALLERY_FILTER = {"//toonily.com/[\\w\\-]+/[\\w\\-]+[/]{0,1}$"};
    private static final String[] DIRTY_ELEMENTS = {".c-ads"};
    private static final String[] JS_CONTENT_BLACKLIST = {"'iframe'","'adsdomain'","'closead'"};
    private static final String[] BLOCKED_CONTENT = {".cloudfront.net"};

    Site getStartSite() {
        return Site.TOONILY;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.adBlocker.addToUrlBlacklist(BLOCKED_CONTENT);
        client.adBlocker.addUrlWhitelist(DOMAIN_FILTER);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
