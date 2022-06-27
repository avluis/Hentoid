package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class Hentai2ReadActivity extends BaseWebActivity {

    public static final String GALLERY_PATTERN = "//hentai2read.com/[\\w\\-]+/$";

    private static final String DOMAIN_FILTER = "hentai2read.com";
    private static final String[] GALLERY_FILTER = {GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "[0-9\\.]+/$"};
    private static final String[] DIRTY_ELEMENTS = {"div[data-refresh]"}; // iframe[src*=ads]

    Site getStartSite() {
        return Site.HENTAI2READ;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(DIRTY_ELEMENTS);
        return client;
    }
}
