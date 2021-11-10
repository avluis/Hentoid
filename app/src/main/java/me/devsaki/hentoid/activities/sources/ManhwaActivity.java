package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class ManhwaActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "manhwahentai.me";
    private static final String[] GALLERY_FILTER = {"//manhwahentai.me/[\\w\\-]+/[\\w\\-]{3,}/$"};
    private static final String[] DIRTY_ELEMENTS = {".c-ads"};
    private static final String[] JS_CONTENT_BLACKLIST = {"'iframe'","'adsdomain'","'closead'"};
    private static final String[] BLOCKED_CONTENT = {".cloudfront.net"};


    Site getStartSite() {
        return Site.MANHWA;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.adBlocker.addToUrlBlacklist(BLOCKED_CONTENT);
        client.adBlocker.addJsUrlWhitelist(DOMAIN_FILTER);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
