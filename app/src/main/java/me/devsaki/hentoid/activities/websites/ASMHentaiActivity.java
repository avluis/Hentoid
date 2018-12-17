package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.ASMHentaiServer;
import me.devsaki.hentoid.retrofit.EHentaiServer;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by avluis on 07/21/2016.
 * Implements ASMHentai source
 */
public class ASMHentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "asmhentai.com";
    private static final String GALLERY_FILTER = "asmhentai.com/g/";
    private static final String[] blockedContent = {"f.js"};

    Site getStartSite() {
        return Site.ASMHENTAI;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new ASMViewClient(GALLERY_FILTER, getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

/*
    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }
*/

    private class ASMViewClient extends CustomWebViewClient {

        ASMViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            super(filteredUrl, startSite, listener);
            addContentBlockFilter(blockedContent);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            Pattern pattern = Pattern.compile(GALLERY_FILTER);
            Matcher matcher = pattern.matcher(url);

            if (matcher.find()) {
                String[] galleryUrlParts = url.split("/");
                compositeDisposable.add(ASMHentaiServer.API.getGalleryMetadata(galleryUrlParts[4])
                        .observeOn(Schedulers.newThread()) // Consider calling Schedulers.shutdown() if Schedulers.io or Schedulers.computation is used instead
                        .subscribe(
                                metadata -> listener.onResultReady(metadata.toContent(), 1), throwable -> {
                                    Timber.e(throwable, "Error parsing content.");
                                    listener.onResultFailed("");
                                })
                );
            }
        }

/*
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
*/
    }
}
