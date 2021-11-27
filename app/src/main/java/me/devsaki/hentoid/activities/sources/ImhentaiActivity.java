package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class ImhentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "imhentai.xxx";
    private static final String[] GALLERY_FILTER = {"//imhentai.xxx/gallery/"};
    //    private static final String[] DIRTY_ELEMENTS = {".bblocktop"}; <-- fucks up the CSS when removed
    private static final String[] DIRTY_ELEMENTS = {".er_container"};

    Site getStartSite() {
        return Site.IMHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }
}
