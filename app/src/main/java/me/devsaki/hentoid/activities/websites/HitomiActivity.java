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
import me.devsaki.hentoid.retrofit.HitomiServer;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

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
        CustomWebViewClient client = new HitomiWebViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }


    private class HitomiWebViewClient extends CustomWebViewClient {

        HitomiWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, startSite, listener);
            addContentBlockFilter(blockedContent);
        }

        @Override
        protected void onGalleryFound(String url) {
            String[] galleryUrlParts = url.split("/");
            compositeDisposable.add(HitomiServer.API.getGalleryMetadata(galleryUrlParts[galleryUrlParts.length - 1])
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
            if (url.contains("hitomi.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "hitomi.js", TYPE.JS);
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
            if (url.contains("hitomi.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "hitomi.js", TYPE.JS);
            } else if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
