package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentai.cafe";
    private static final String[] GALLERY_FILTER = {"//hentai.cafe/hc.fyi/[0-9]+$"};

    Site getStartSite() {
        return Site.HENTAICAFE;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
