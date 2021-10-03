package me.devsaki.hentoid.activities.sources;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.LusciousContent;
import me.devsaki.hentoid.parsers.content.PixivContent;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class PixivActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "pixiv.net";
    public static final String[] GALLERY_FILTER = {
            "xxx", // Fetch using GraphQL call
            "pixiv.net/[\\w\\-]+/artworks/[0-9]+$", // Illustrations page
            "pixiv.net/user/[0-9]+/series/[0-9]+$" // Manga/series page
    };
    //private static final String[] DIRTY_ELEMENTS = {".ad_banner"}; <-- doesn't work; added dynamically on an element tagged with a neutral-looking class


    Site getStartSite() {
        return Site.PIXIV;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        PixivWebClient client = new PixivWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
//        client.adBlocker.addUrlWhitelist(DOMAIN_FILTER);
        //client.addDirtyElements(DIRTY_ELEMENTS);

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

                    // Scram if the response is empty
                    ResponseBody body = response.body();
                    if (null == body) throw new IOException("Empty body");

                    return HttpHelper.okHttpResponseToWebkitResponse(response, body.byteStream());
                } catch (IOException e) {
                    Timber.w(e);
                }
            }

            return super.shouldInterceptRequest(view, request);
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            activity.onGalleryPageStarted();

            ContentParser contentParser = new PixivContent();
            compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            content -> super.processContent(content, content.getGalleryUrl(), quickDownload),
                            Timber::e
                    )
            );
            return null;
        }
    }
}
