package me.devsaki.hentoid.activities.sources;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.AnchiraGalleryMetadata;
import me.devsaki.hentoid.parsers.content.AnchiraContent;
import me.devsaki.hentoid.parsers.images.AnchiraParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.AnchiraBackgroundWebView;
import timber.log.Timber;

/**
 * Implements Anchira.to source
 */
public class AnchiraActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "anchira.to";
    private static final String[] GALLERY_FILTER = {"//anchira.to/g/[\\w\\-]+/[\\w\\-]+$", "//anchira.to/api/v1/library/[\\w\\-]+/[\\w\\-]+$"};

    Site getStartSite() {
        return Site.ANCHIRA;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        AnchiraWebClient client = new AnchiraWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.setJsStartupScripts("anchira_pages.js");
        webView.addJavascriptInterface(new AnchiraBackgroundWebView.AnchiraJsInterface(s -> client.jsHandler(s, false)), "anchiraJsInterface");
        return client;
    }

    private class AnchiraWebClient extends CustomWebViewClient {

        AnchiraWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            activity.onGalleryPageStarted();

            Content content = new AnchiraContent().toContent(urlStr);
            AnchiraParser parser = new AnchiraParser();
            try {
                //parser.parseImageListWithWebview(content, webView); // Only fetch them when queue is processed
                content.setStatus(StatusContent.SAVED);
            } catch (Exception e) {
                Helper.logException(e);
                Timber.i(e);
                content.setStatus(StatusContent.IGNORED);
            }

            return null;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
            return AnchiraBackgroundWebView.Companion.shouldInterceptRequestInternal(view, request, site);
        }

        public void jsHandler(AnchiraGalleryMetadata a, boolean quickDownload) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                Content content = a.toContent();
                processContent(content, content.getGalleryUrl(), quickDownload);
                activity.onResultReady(content, quickDownload);
            });
        }
    }
}