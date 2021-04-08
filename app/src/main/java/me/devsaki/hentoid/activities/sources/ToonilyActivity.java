package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;

public class ToonilyActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "toonily.com";
    private static final String[] GALLERY_FILTER = {"//toonily.com/[\\w\\-]+/[\\w\\-]+/$"};
    private static final String[] DIRTY_ELEMENTS = {".c-ads"};
    private static final String[] BLOCKED_CONTENT = {".cloudfront.net"};
    private static final String[] JS_WHITELIST = {"//toonily.com/", "disqus", "googletagmanager.com", "cloudflare"};

    Site getStartSite() {
        return Site.TOONILY;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addContentBlockFilter(BLOCKED_CONTENT);
        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    /**
     * Specific implementation to get rid of ad js files
     * that have random names
     */
    @Override
    protected boolean isUrlForbidden(@NonNull String url) {
        // 1- Process usual blacklist
        if (super.isUrlForbidden(url)) return true;

        // 2- Accept non-JS files
        if (!HttpHelper.getExtensionFromUri(url).equals("js")) return false;

        // 3- Accept JS files defined in the whitelist
        for (String s : JS_WHITELIST) {
            if (url.toLowerCase().contains(s)) return false;
        }

        // 4- Block all other JS files
        return true;
    }
}
