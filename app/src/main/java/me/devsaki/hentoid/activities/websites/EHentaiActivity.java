package me.devsaki.hentoid.activities.websites;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.parsers.content.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.EHentaiServer;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Implements E-Hentai source
 */
public class EHentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "e-hentai.org";
    private static final String GALLERY_FILTER = "e-hentai.org/g/[0-9]+/[A-Za-z0-9\\-_]+";

    Site getStartSite() {
        return Site.EHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new EHentaiWebClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(String filter, Site startSite, ResultListener<Content> listener) {
            super(filter, startSite, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);
            compositeDisposable.add(EHentaiServer.API.getGalleryMetadata(query)
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                listener.onResultFailed("");
                            })
            );
        }
    }
}
