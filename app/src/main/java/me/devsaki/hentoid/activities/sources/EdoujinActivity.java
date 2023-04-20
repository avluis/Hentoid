package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class EdoujinActivity extends BaseWebActivity {

    public static final String GALLERY_PATTERN = "edoujin.net/manga/[\\w\\-_%]+/$";

    private static final String DOMAIN_FILTER = "edoujin.net";
    private static final String[] GALLERY_FILTER = {GALLERY_PATTERN, GALLERY_PATTERN.replace("/$", "\\-") + "[0-9]+/$"};

    Site getStartSite() {
        return Site.EDOUJIN;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
