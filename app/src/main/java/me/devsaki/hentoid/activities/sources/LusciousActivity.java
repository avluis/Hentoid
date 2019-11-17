package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class LusciousActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "luscious.net";
    private static final String GALLERY_FILTER = "luscious.net/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-_]+_[0-9]+/$";

    Site getStartSite() {
        return Site.LUSCIOUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
