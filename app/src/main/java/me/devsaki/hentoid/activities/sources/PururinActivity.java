package me.devsaki.hentoid.activities.sources;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.sources.PururinServer;
import timber.log.Timber;

public class PururinActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pururin.io";
    private static final String GALLERY_FILTER = "//pururin.io/gallery/";

    Site getStartSite() {
        return Site.PURURIN;
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
            compositeDisposable.add(PururinServer.API.getGalleryMetadata(galleryUrlParts[galleryUrlParts.length - 2], galleryUrlParts[galleryUrlParts.length - 1])
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
