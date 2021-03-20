package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class ImhentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "imhentai.xxx";
    private static final String[] GALLERY_FILTER = {"//imhentai.xxx/gallery/"};
//    private static final String[] DIRTY_ELEMENTS = {".bblocktop"}; <-- fucks up the CSS when removed

    Site getStartSite() {
        return Site.IMHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
//        addDirtyElements(DIRTY_ELEMENTS);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
}
