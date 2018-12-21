package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.PandaServer;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Implements MangaPanda source
 */
public class PandaActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "mangapanda.com";
    private static final String GALLERY_FILTER = "mangapanda.com/[A-Za-z0-9\\-_]+/[0-9]+";

    Site getStartSite() {
        return Site.PANDA;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new PandaWebViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class PandaWebViewClient extends CustomWebViewClient {

        PandaWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, startSite, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            compositeDisposable.add(PandaServer.API.getGalleryMetadata(galleryUrlParts[galleryUrlParts.length - 2], galleryUrlParts[galleryUrlParts.length - 1])
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                listener.onResultFailed("");
                            })
            );
        }
    }
}
