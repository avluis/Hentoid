package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.NhentaiServer;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

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
        CustomWebViewClient client = new NhentaiWebViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {

        NhentaiWebViewClient(String galleryUrl, Site startSite, ResultListener<Content> listener) {
            super(galleryUrl, startSite, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            compositeDisposable.add(NhentaiServer.API.getGalleryMetadata(galleryUrlParts[galleryUrlParts.length - 1])
                    .subscribe(
                            metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                listener.onResultFailed("");
                            })
            );
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_style.css", TYPE.CSS);
            } else if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_style.css", TYPE.CSS);
            } else if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
