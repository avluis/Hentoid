package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.images.HitomiParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hitomi.la";
    private static final String[] GALLERY_FILTER = {"//hitomi.la/[manga|doujinshi|gamecg|cg]+/[^/]+-[0-9]{2,}.html(#[0-9]{1,2}){0,1}$"};
    private static final String[] RESULTS_FILTER = {"//hitomi.la[/]{0,1}$", "//hitomi.la[/]{0,1}\\?", "//hitomi.la/search.html", "//hitomi.la/index-[\\w%\\-\\.\\?]+", "//hitomi.la/(series|artist|tag|character)/[\\w%\\-\\.\\?]+"};
    private static final String[] BLOCKED_CONTENT = {"hitomi-horizontal.js", "hitomi-vertical.js", "invoke.js", "ion.sound"};
    private static final String[] JS_URL_PATTERN_WHITELIST = {"//hitomi.la[/]{0,1}$", "galleries/[\\w%\\-]+.js$", "//hitomi.la/[?]page=[0-9]+"};
    private static final String[] JS_URL_WHITELIST = {"nozomiurlindex", "languagesindex", "tagindex", "filesaver", "common", "date", "download", "gallery", "jquery", "cookie", "jszip", "limitlists", "moment-with-locales", "moveimage", "pagination", "search", "searchlib", "yall", "reader", "decode_webp", "bootstrap", "gg.js", "paging", "language_support"};
    private static final String[] JS_CONTENT_BLACKLIST = {"async", "exoloader", "popunder", "da_etirw", "ad_trigger_class", "ad_popup_force", "exosrv.com", "realsrv.com", "ad-provider", "adprovider"};
    private static final String[] REMOVABLE_ELEMENTS = {
            ".content div[class^=hitomi-]",
            ".container div[class^=hitomi-]",
            ".top-content > div:not(.list-title)",
            ".content > div:not(.gallery,.cover-column,.gallery-preview)",
            ".wnvtqvsW"
    };

    Site getStartSite() {
        return Site.HITOMI;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        languageFilterButton.setOnClickListener(v -> onLangFilterButtonClick());
    }

    @Override
    public void onPageStarted(
            String url,
            boolean isGalleryPage,
            boolean isHtmlLoaded,
            boolean isBookmarkable,
            List<String> jsStartupScripts) {
        languageFilterButton.setVisibility(View.INVISIBLE);
        super.onPageStarted(url, isGalleryPage, isHtmlLoaded, isBookmarkable, jsStartupScripts);
    }

    @Override
    public void onPageFinished(boolean isResultsPage, boolean isGalleryPage) {
        if (Preferences.isLanguageFilterButton() && isUrlFilterable(webView.getUrl()))
            languageFilterButton.setVisibility(View.VISIBLE);
        else
            languageFilterButton.setVisibility(View.INVISIBLE);

        super.onPageFinished(isResultsPage, isGalleryPage);
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        HitomiWebClient client = new HitomiWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        //client.addHideableElements(HIDEABLE_ELEMENTS);
        client.addRemovableElements(REMOVABLE_ELEMENTS);
        client.addJavascriptBlacklist(JS_CONTENT_BLACKLIST);
        client.setResultsUrlPatterns(RESULTS_FILTER);
        client.setResultUrlRewriter(this::rewriteResultsUrl);
        client.adBlocker.addToUrlBlacklist(BLOCKED_CONTENT);
        client.adBlocker.addToJsUrlWhitelist(JS_URL_WHITELIST);
        for (String s : JS_URL_PATTERN_WHITELIST) client.adBlocker.addJsUrlPatternWhitelist(s);
        for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client;
    }

    private String rewriteResultsUrl(@NonNull Uri resultsUri, int page) {
        Uri.Builder builder = resultsUri.buildUpon();

        if (resultsUri.toString().contains("search"))
            builder.fragment(page + ""); // https://hitomi.la/search.html?<searchTerm>#<page>
        else {
            Map<String, String> params = HttpHelper.parseParameters(resultsUri);
            params.put("page", page + "");

            builder.clearQuery();
            for (Map.Entry<String, String> param : params.entrySet())
                builder.appendQueryParameter(param.getKey(), param.getValue());
        }

        return builder.toString();
    }

    private void onLangFilterButtonClick() {
        String temp = Preferences.getLanguageFilterButtonValue().concat(".html").toLowerCase();
        if (webView.getUrl().contains("-all.html"))
            webView.loadUrl(webView.getUrl().replace("all.html", temp));
        else
            webView.loadUrl(webView.getUrl().concat("index-").concat(temp));
    }

    private boolean isUrlFilterable(String url) {
        //only works on 1st page
        if (url.equals("https://hitomi.la/") || url.equals("https://hitomi.la/?page=1") || (url.endsWith("-all.html") || url.endsWith("-all.html?page=1")))
            return true;
        else
            return false;
    }

    private class HitomiWebClient extends CustomWebViewClient {

        HitomiWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();

            if ((isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags()) && url.contains("galleryblock")) { // Process book blocks to mark existing ones
                WebResourceResponse result = parseResponse(url, request.getRequestHeaders(), false, false);
                if (result != null) return result;
                else return sendRequest(request);
            }

            return super.shouldInterceptRequest(view, request);
        }

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
            HitomiParser parser = new HitomiParser();
            try {
                /*List<ImageFile> images =*/
                parser.parseImageListWithWebview(content, webView); // Only fetch them when queue is processed
                //content.setImageFiles(images);
                content.setStatus(StatusContent.SAVED);
            } catch (Exception e) {
                Timber.i(e);
                content.setStatus(StatusContent.IGNORED);
            }

            return super.processContent(content, url, quickDownload);
        }
    }
}
