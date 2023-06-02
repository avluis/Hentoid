package me.devsaki.hentoid.activities.sources;

import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.PixivContent;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class PixivActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = ".pixiv.net";
    private static final String[] GALLERY_FILTER = {
            "pixiv.net/touch/ajax/illust/details\\?", // Illustrations page (single gallery) / load using fetch call
            "pixiv.net/touch/ajax/illust/series_content/", // Manga/series page (anthology) / load using fetch call
            "pixiv.net/touch/ajax/user/details\\?", // User page / load using fetch call
            "pixiv.net/[\\w\\-]+/artworks/[0-9]+$", // Illustrations page (single gallery)
            "pixiv.net/user/[0-9]+/series/[0-9]+$", // Manga/series page (anthology)
            "pixiv.net/users/[0-9]+$" // User page
    };
    private static final String[] BLOCKED_CONTENT = {"ads-pixiv.net"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};

    private static final String[] NAVIGATION_QUERIES = {"/details?", "/search/illusts?", "ajax/pages/top?", "/tag_stories?"};


    Site getStartSite() {
        return Site.PIXIV;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        PixivWebClient client = new PixivWebClient(getStartSite(), GALLERY_FILTER, this);
        client.adBlocker.addToUrlBlacklist(BLOCKED_CONTENT);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);
        client.setJsStartupScripts("pixiv.js");
        webView.addJavascriptInterface(new pixivJsInterface(), "pixivJsInterface");

        return client;
    }

    private static class PixivWebClient extends CustomWebViewClient {

        PixivWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Kill CORS
            if (url.contains("s.pximg.net")) {
                try {
                    Response response = HttpHelper.getOnlineResourceFast(
                            url,
                            HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), url),
                            Site.PIXIV.useMobileAgent(), Site.PIXIV.useHentoidAgent(), Site.PIXIV.useWebviewAgent()
                    );

                    // Scram if the response is a redirection or an error
                    if (response.code() >= 300) return null;

                    // Scram if the response is empty
                    ResponseBody body = response.body();
                    if (null == body) throw new IOException("Empty body");

                    return HttpHelper.okHttpResponseToWebkitResponse(response, body.byteStream());
                } catch (IOException e) {
                    Timber.w(e);
                }
            }

            // Gray out the action button after every navigation action
            for (String s : NAVIGATION_QUERIES)
                if (url.contains(s)) {
                    compositeDisposable.add(
                            Completable.fromRunnable(() -> activity.onPageStarted(url, isGalleryPage(url), false, false, null))
                                    .subscribeOn(AndroidSchedulers.mainThread())
                                    .subscribe()
                    );
                }

            return super.shouldInterceptRequest(view, request);
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            if (analyzeForDownload || quickDownload) {
                activity.onGalleryPageStarted();

                if (BuildConfig.DEBUG) Timber.v("WebView : parseResponse Pixiv %s", urlStr);

                ContentParser contentParser = new PixivContent();
                compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.computation())
                        .map(content -> super.processContent(content, content.getGalleryUrl(), quickDownload))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                content2 -> activity.onResultReady(content2, quickDownload),
                                Timber::e
                        )
                );
            }
            return null;
        }
    }

    public class pixivJsInterface {
        @JavascriptInterface
        @SuppressWarnings("unused")
        public String getPixivCustomCss() {
            return getCustomCss();
        }
    }
}
