package me.devsaki.hentoid.activities.sources;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.io.IOException;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.AnchiraGalleryMetadata;
import me.devsaki.hentoid.parsers.images.AnchiraParser;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.views.AnchiraBackgroundWebView;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Implements Anchira.to source
 */
public class AnchiraActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "anchira.to";
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER};

    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder", "adGuardBase", "adtrace.online", "Admanager"};
    private static final String[] GALLERY_FILTER = {"//anchira.to/g/[\\w\\-]+/[\\w\\-]+$"/*, "//anchira.to/api/v1/library/[\\w\\-]+/[\\w\\-]+$"*/};

    Site getStartSite() {
        return Site.ANCHIRA;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        AnchiraWebClient client = new AnchiraWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);

        webView.addJavascriptInterface(new AnchiraBackgroundWebView.AnchiraJsInterface(s -> client.jsHandler(s, false)), "wysiwygInterface");

        return client;
    }

    public static class AnchiraWebClient extends CustomWebViewClient {

        public AnchiraWebClient(Site site, String[] galleryUrl, WebResultConsumer resultConsumer) {
            super(site, galleryUrl, resultConsumer);
            setJsStartupScripts("wysiwyg_parser.js");
        }

        AnchiraWebClient(Site site, String[] galleryUrl, CustomWebActivity activity) {
            super(site, galleryUrl, activity);
            setJsStartupScripts("wysiwyg_parser.js");
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Kill CORS
            if (url.contains(AnchiraGalleryMetadata.IMG_HOST) && request.getMethod().equalsIgnoreCase("options")) {
                try {
                    Response response = HttpHelper.optOnlineResourceFast(
                            url,
                            HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), url),
                            Site.ANCHIRA.useMobileAgent(), Site.ANCHIRA.useHentoidAgent(), Site.ANCHIRA.useWebviewAgent()
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

            /*
            WebResourceResponse res = AnchiraBackgroundWebView.Companion.shouldInterceptRequestInternal(view, request, site);
            if (null == res) return super.shouldInterceptRequest(view, request);
            else return res;
             */
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        protected Content processContent(@NonNull Content content, @NonNull String url, boolean quickDownload) {
            if (quickDownload) {
                // Use a background Wv to get book attributes when targeting another page (quick download)
                AnchiraParser parser = new AnchiraParser();
                try {
                    parser.parseImageListWithWebview(content);
                    content.setStatus(StatusContent.SAVED);
                    if (activity != null) activity.onGalleryPageStarted();
                } catch (Exception e) {
                    Timber.w(e);
                    content.setStatus(StatusContent.IGNORED);
                }
            }

            return super.processContent(content, url, quickDownload);
        }

        public void jsHandler(Content content, boolean quickDownload) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                processContent(content, content.getGalleryUrl(), quickDownload);
                resConsumer.onResultReady(content, quickDownload);
            });
        }
    }
}
