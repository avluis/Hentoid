package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;

public class ManhwaActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "manhwahentai.me";
    private static final String[] GALLERY_FILTER = {"//manhwahentai.me/[\\w\\-]+/[\\w\\-]+/$"};
    private static final String[] DIRTY_ELEMENTS = {".c-ads"};
    private static final String[] BLOCKED_CONTENT = {".cloudfront.net"};


    Site getStartSite() {
        return Site.MANHWA;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new ManhwaWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addToUrlBlacklist(BLOCKED_CONTENT);
        client.addUrlWhitelist(DOMAIN_FILTER);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }

    private static class ManhwaWebViewClient extends CustomWebViewClient {

        ManhwaWebViewClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        /**
         * Specific implementation to get rid of ad js files that have random names
         */
        @Override
        protected boolean isUrlBlacklisted(@NonNull String url) {
            // 1- Process usual blacklist
            if (super.isUrlBlacklisted(url)) return true;

            // 2- Accept non-JS files
            if (!HttpHelper.getExtensionFromUri(url).equals("js")) return false;

            // 3- Accept JS files defined in the whitelist; block others
            return !super.isUrlWhitelisted(url);
        }
    }

}
