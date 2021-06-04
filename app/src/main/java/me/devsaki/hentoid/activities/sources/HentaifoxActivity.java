package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class HentaifoxActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentaifox.com";
    private static final String[] GALLERY_FILTER = {"hentaifox.com/gallery/[0-9]+/$"};

    Site getStartSite() {
        return Site.HENTAIFOX;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
