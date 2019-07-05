package me.devsaki.hentoid.activities.sources;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.sources.NexusServer;
import timber.log.Timber;

public class NexusActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentainexus.com";
    private static final String GALLERY_FILTER = "//hentainexus.com/view/";

    Site getStartSite() {
        return Site.NEXUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new PururinViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class PururinViewClient extends CustomWebViewClient {

        PururinViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            compositeDisposable.add(NexusServer.API.getGalleryMetadata(galleryUrlParts[galleryUrlParts.length - 1])
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                listener.onResultFailed("");
                            })
            );
        }
    }
}
