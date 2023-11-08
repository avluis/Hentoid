package me.devsaki.hentoid.activities.sources;

import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.content.AnchiraContent;
import me.devsaki.hentoid.parsers.images.AnchiraParser;
import me.devsaki.hentoid.util.Helper;
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
        //client.setJsStartupScripts("anchira_pages.js");
        //webView.addJavascriptInterface(new AnchiraJsInterface(client::jsHandler), "anchiraJsInterface");
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
                parser.parseImageListWithWebview(content, webView); // Only fetch them when queue is processed
                content.setStatus(StatusContent.SAVED);
            } catch (Exception e) {
                Helper.logException(e);
                Timber.i(e);
                content.setStatus(StatusContent.IGNORED);
            }

            return null;
        }
/*
        @Override
        protected Content processContent(@NonNull Content content, @NonNull String url, boolean quickDownload) {
            // Wait until the page's resources are all loaded
            if (!quickDownload) {
                Timber.v(">> not loading");
                while (!isLoading()) Helper.pause(20);
                Timber.v(">> loading");
                while (isLoading()) Helper.pause(100);
                Timber.v(">> done");
            }
            AnchiraParser parser = new AnchiraParser();
            try {
                parser.parseImageListWithWebview(content, webView); // Only fetch them when queue is processed
                content.setStatus(StatusContent.SAVED);
            } catch (Exception e) {
                Helper.logException(e);
                Timber.i(e);
                content.setStatus(StatusContent.IGNORED);
            }

            return super.processContent(content, url, quickDownload);
        }

 */

        /*
        public void jsHandler(String a, String b) {
            // TODO
        }
         */
    }

    /*
    public static class AnchiraJsInterface {

        private final BiConsumer<String, String> handler;

        public AnchiraJsInterface(BiConsumer<String, String> handler) {
            this.handler = handler;
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void anchiraJsInterface(String a, String b) {
            Timber.d("anchira %s : %s", a, b);
            handler.accept(a, b);
        }
    }
     */
}
