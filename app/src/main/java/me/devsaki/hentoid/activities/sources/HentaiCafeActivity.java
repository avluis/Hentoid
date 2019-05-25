package me.devsaki.hentoid.activities.sources;

import android.net.Uri;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.sources.HentaiCafeServer;
import timber.log.Timber;

import static me.devsaki.hentoid.enums.Site.HENTAICAFE;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentai.cafe";
    private static final String GALLERY_FILTER = "//hentai.cafe/[^/]+/$";

    Site getStartSite() {
        return Site.HENTAICAFE;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new HentaiCafeWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class HentaiCafeWebViewClient extends CustomWebViewClient {

        HentaiCafeWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            if (    url.startsWith(HENTAICAFE.getUrl() + "/78-2/")          // ignore tags page
                    || url.startsWith(HENTAICAFE.getUrl() + "/artists/")    // ignore artist page
                    || url.startsWith(HENTAICAFE.getUrl() + "/?s=")         // ignore text search results
                ) {
                return;
            }

            String[] galleryUrlParts = url.split("/");
            compositeDisposable.add(HentaiCafeServer.API.getGalleryMetadata(Uri.decode(galleryUrlParts[galleryUrlParts.length - 1]))
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
