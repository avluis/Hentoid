package me.devsaki.hentoid.activities.sources;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

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
    private static final String[] GALLERY_FILTER = {"//anchira.to/g/[\\w\\-]+/[\\w\\-]+$"};

    Site getStartSite() {
        return Site.ANCHIRA;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        AnchiraWebClient client = new AnchiraWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        client.adBlocker.addToJsUrlWhitelist(JS_WHITELIST);

        webView.addJavascriptInterface(new AnchiraBackgroundWebView.AnchiraJsContentInterface(s -> client.jsHandler(s, false)), "wysiwygInterface");

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
            if (url.contains(AnchiraGalleryMetadata.IMG_HOST)) {
                String[] parts = url.split("/");
                if (parts.length > 7) {
                    if (request.getMethod().equalsIgnoreCase("options")) {
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
                    // TODO that's ugly; find a more suitable interface; e.g. onImagesReady
                    Content c = new Content();
                    c.setSite(Site.ANCHIRA);
                    c.setCoverImageUrl(url);
                    resConsumer.onContentReady(c, false);
                }
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            // Complete override of default behaviour because
            // - There's no HTML to be parsed for ads
            // - The interesting parts are loaded by JS, not now

            if (quickDownload) {
                // Use a background Wv to get book attributes when targeting another page (quick download)
                AnchiraParser parser = new AnchiraParser();
                try {
                    Content content = parser.parseContentWithWebview(urlStr);
                    content.setStatus(StatusContent.SAVED);
                    if (activity != null) activity.onGalleryPageStarted();
                    final Content contentFinal = super.processContent(content, urlStr, true);
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        resConsumer.onContentReady(contentFinal, true);
                    });
                } catch (Exception e) {
                    Timber.w(e);
                } finally {
                    parser.destroy();
                }
            }

            return null;
        }

        public void destroy() {
            super.destroy();
        }

        public void jsHandler(Content content, boolean quickDownload) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                processContent(content, content.getGalleryUrl(), quickDownload);
                resConsumer.onContentReady(content, quickDownload);
            });
        }
    }
}
