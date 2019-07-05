package me.devsaki.hentoid.activities.sources;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;

/**
 * Created by Shiro on 1/20/2016.
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hitomi.la";
    private static final String GALLERY_FILTER = "//hitomi.la/galleries/";
    private static final String[] blockedContent = {"hitomi-horizontal.js", "hitomi-vertical.js"};

    Site getStartSite() {
        return Site.HITOMI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new HitomiWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }


    private class HitomiWebViewClient extends CustomWebViewClient {

        HitomiWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
            addContentBlockFilter(blockedContent);
        }
    }
}
