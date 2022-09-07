package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PorncomixActivity extends BaseWebActivity {

    private static final String[] DOMAIN_FILTER = {"www.porncomixonline.net", "www.porncomixonline.com", "porncomicszone.net", "porncomixinfo.com", "porncomixinfo.net", "bestporncomix.com"};
    private static final String[] GALLERY_FILTER = {
            "//www.porncomixonline.(com|net)/(?!m-comic)([\\w\\-]+)/[\\w\\-]+/$",
            "//www.porncomixonline.(com|net)/m-comic/[\\w\\-]+/[\\w\\-]+$",
            "//www.porncomixonline.(com|net)/m-comic/[\\w\\-]+/[\\w\\-]+/$",
            "//www.porncomixonline.com/(?!m-comic)([\\w\\-]+)/[\\w\\-]+/$",
            "//www.porncomixonline.com/m-comic/[\\w\\-]+/[\\w\\-]+$",
            "//porncomicszone.net/[0-9]+/[\\w\\-]+/[0-9]+/$",
            "//porncomixinfo.(com|net)/manga-comics/[\\w\\-]+/[\\w\\-]+/$",
            "//porncomixinfo.(com|net)/chapter/[\\w\\-]+/[\\w\\-]+/$",
            "//bestporncomix.com/gallery/[\\w\\-]+/$"
    };
    private static final String[] DIRTY_ELEMENTS = {"iframe[name^='spot']"};

    Site getStartSite() {
        return Site.PORNCOMIX;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addRemovableElements(DIRTY_ELEMENTS);
        return client;
    }
}
