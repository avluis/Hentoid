package me.devsaki.hentoid.activities.websites;

import android.graphics.Bitmap;
import android.webkit.WebView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleryQuery;
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
        CustomWebViewClient client = new EHentaiWebClient(getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }
/*
    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(getStartSite()), extra);
    }
*/


    private class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(Site startSite, ResultListener<Content> listener) {
            super(startSite, listener);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            Pattern pattern = Pattern.compile(GALLERY_FILTER);
            Matcher matcher = pattern.matcher(url);

            if (matcher.find()) {
                String[] galleryUrlParts = url.split("/");
                EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);
                compositeDisposable.add(EHentaiServer.API.getGalleryMetadata(query)
                        .observeOn(Schedulers.newThread()) // Consider calling Schedulers.shutdown() if Schedulers.io or Schedulers.computation is used instead
                        .subscribe(
                                metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                    Timber.e(throwable, "Error parsing content.");
                                    listener.onResultFailed("");
                                })
                );
            }
        }
    }
}
