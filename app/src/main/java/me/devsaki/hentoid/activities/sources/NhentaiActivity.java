package me.devsaki.hentoid.activities.sources;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.sources.NhentaiServer;
import timber.log.Timber;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 */
public class NhentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "nhentai.net";
    private static final String GALLERY_FILTER = "nhentai.net/g/";

    Site getStartSite() {
        return Site.NHENTAI;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new NhentaiWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {

        NhentaiWebViewClient(String galleryUrl, ResultListener<Content> listener) {
            super(galleryUrl, listener);
        }
    }
}
