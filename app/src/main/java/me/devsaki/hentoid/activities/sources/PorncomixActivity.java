package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

public class PorncomixActivity extends BaseWebActivity {

    private static final String[] GALLERY_FILTER = {
            "//www.porncomixonline.net/manga/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-]+$",
            "//www.porncomixonline.net/[A-Za-z0-9\\-]+/$",
            "//porncomicszone.net/[0-9]+/[A-Za-z0-9\\-]+/[0-9]+/$",
            "//porncomixinfo.com/manga-comics/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-]+/$",
            "//bestporncomix.com/gallery/[A-Za-z0-9\\-]+/$"
    };
    private static final String[] DIRTY_ELEMENTS = {"iframe[name^='spot']"};

    Site getStartSite() {
        return Site.PORNCOMIX;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addDirtyElements(DIRTY_ELEMENTS);
        return new CustomWebViewClient(GALLERY_FILTER, this);
    }
}
