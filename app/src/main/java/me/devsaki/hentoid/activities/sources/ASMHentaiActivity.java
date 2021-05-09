package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.enums.Site;

/**
 * Created by avluis on 07/21/2016.
 * Implements ASMHentai source
 */
public class ASMHentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "asmhentai.com";
    private static final String[] GALLERY_FILTER = {"asmhentai.com/g/"};
    private static final String[] blockedContent = {"f.js"};

    Site getStartSite() {
        return Site.ASMHENTAI;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addContentBlockFilter(blockedContent);
        return client;
    }
}
