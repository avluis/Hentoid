package me.devsaki.hentoid.activities.sources;

import android.os.Build;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.SimplyApiContent;
import timber.log.Timber;

public class SimplyActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "simply-hentai.com";
    public static final String[] GALLERY_FILTER = {"simply-hentai.com/[%\\w\\-]+/[%\\w\\-]+$", "api.simply-hentai.com/v3/[%\\w\\-]+/[%\\w\\-]+$"};
    //private static final String[] JS_WHITELIST = {DOMAIN_FILTER + "/cdn", DOMAIN_FILTER + "/wp"};
    //private static final String[] JS_CONTENT_BLACKLIST = {"var exoloader;", "popunder"};
    //private static final String[] AD_ELEMENTS = {"iframe", ".c-ads"};


    Site getStartSite() {
        return Site.SIMPLY;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        SimplyViewClient client = new SimplyViewClient(getStartSite(), GALLERY_FILTER, this, webView);
        client.restrictTo(DOMAIN_FILTER);
        //client.addRemovableElements(AD_ELEMENTS);
        //client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);
        //for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);

        //xhrHandler = client::onXhrCall;

        return client;
    }


    private static class SimplyViewClient extends CustomWebViewClient {

        private SimplyViewSwClient swClient = null;

        SimplyViewClient(Site site, String[] filter, CustomWebActivity activity, @NonNull WebView webView) {
            super(site, filter, activity);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                ServiceWorkerController swController = ServiceWorkerController.getInstance();
                swClient = new SimplyViewSwClient(this, webView);
                swController.setServiceWorkerClient(swClient);
            }

        }

        @Override
        void destroy() {

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                if (swClient != null) {
                    swClient.destroy();
                    swClient = null;
                }
            }

            super.destroy();
        }

        /*
        public void onXhrCall(String url, String body) {
            if (!isGalleryPage(url)) return;
            Timber.i("XHR %s %s", url, body);
            parseResponse(url, null, true, false);
        }
         */


        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            // Call the API without using BaseWebActivity.parseResponse
            if (!urlStr.endsWith("/status") && !urlStr.endsWith("/home") && !urlStr.endsWith("/starting")) {
                if (urlStr.contains("api.simply-hentai.com") && (analyzeForDownload || quickDownload)) {
                    activity.onGalleryPageStarted();

                    ContentParser contentParser = new SimplyApiContent();
                    compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                            .subscribeOn(Schedulers.io())
                            .map(content -> super.processContent(content, content.getGalleryUrl(), quickDownload))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    content2 -> activity.onResultReady(content2, quickDownload),
                                    Timber::e
                            )
                    );
                    return null;
                }
                // If calls something else than the API, use the standard parseResponse
                return super.parseResponse(urlStr, requestHeaders, analyzeForDownload, quickDownload);
            }
            return null;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    private static class SimplyViewSwClient extends ServiceWorkerClient {

        private CustomWebViewClient webClient;
        private WebView webView;

        public SimplyViewSwClient(@NonNull CustomWebViewClient client, @NonNull WebView webView) {
            webClient = client;
            this.webView = webView;
        }

        void destroy() {
            webClient = null;
            webView = null;
        }

        public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
            return webClient.shouldInterceptRequest(webView, request);
        }
    }


}
