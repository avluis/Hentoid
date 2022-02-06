package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.images.HitomiParser;
import me.devsaki.hentoid.util.Helper;
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
    private static final String[] JS_URL_PATTERN_WHITELIST = {"//hitomi.la[/]{0,1}$", "galleries/[\\w%\\-]+.js$"};
    private static final String[] JS_URL_WHITELIST = {"nozomiurlindex", "languagesindex", "tagindex", "filesaver", "common", "date", "download", "gallery", "jquery", "cookie", "jszip", "limitlists", "moment-with-locales", "moveimage", "pagination", "search", "searchlib", "yall", "reader", "decode_webp", "bootstrap", "gg.js", "paging", "language_support"};
    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder", "da_etirw"};
    private static final String[] REMOVABLE_ELEMENTS = {".content div[class^=hitomi-]", ".container div[class^=hitomi-]", ".top-content > div:not(.list-title)"};

    Site getStartSite() {
        return Site.HITOMI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        HitomiWebClient client = new HitomiWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        //client.addHideableElements(HIDEABLE_ELEMENTS);
        client.addRemovableElements(REMOVABLE_ELEMENTS);
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

    /*
    private void getContentInfo(@NonNull Content onlineContent, @NonNull Consumer<String> listCallback, boolean fetchGalleryJs) {
        if (null == webView) return;

        // Get the gallery info file
        if (!fetchGalleryJs) {
            runOnUiThread(() -> webView.evaluateJavascript(getJsPagesScript(""), listCallback::accept));
        } else {
            String galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + onlineContent.getUniqueSiteId() + ".js";
            List<Pair<String, String>> headers = new ArrayList<>();
            headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, onlineContent.getReaderUrl()));

            if (extraProcessingDisposable != null)
                extraProcessingDisposable.dispose(); // Cancel whichever process was happening before

            Timber.v(">> fetching gallery JS");
            extraProcessingDisposable = Single.fromCallable(() -> HttpHelper.getOnlineResourceFast(galleryJsonUrl, headers, Site.HITOMI.useMobileAgent(), Site.HITOMI.useHentoidAgent(), Site.HITOMI.useWebviewAgent()))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            response -> {
                                ResponseBody body = response.body();
                                if (null == body) throw new IOException("Empty body");

                                Timber.v(">> Gallery JS ready");
                                String galleryInfo = body.string();
                                Timber.v(">> Running reader JS");
                                webView.evaluateJavascript(getJsPagesScript(galleryInfo), listCallback::accept);
                            },
                            Timber::w
                    );
        }
    }

    // TODO optimize
    private String getJsPagesScript(@NonNull String galleryInfo) {
        StringBuilder sb = new StringBuilder();
        FileHelper.getAssetAsString(getAssets(), "hitomi_pages.js", sb);
        return sb.toString().replace("$galleryInfo", galleryInfo).replace("$webp", Preferences.isDlHitomiWebp() ? "true" : "false");
    }
     */

    private class HitomiWebClient extends CustomWebViewClient {

        HitomiWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (isMarkDownloaded() && url.contains("galleryblock")) { // Process book blocks to mark existing ones
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
                Timber.i(">> not loading");
                while (!isLoading()) Helper.pause(20);
                Timber.i(">> loading");
                while (isLoading()) Helper.pause(100);
                Timber.i(">> done");
            }
            HitomiParser parser = new HitomiParser();
            try {
                List<ImageFile> images = parser.parseImageListWithWebview(content, webView);
                content.setImageFiles(images);
                content.setStatus(StatusContent.SAVED);
            } catch (Exception e) {
                Timber.w(e);
                content.setStatus(StatusContent.IGNORED);
            }
/*
            final AtomicReference<String> imagesStr = new AtomicReference<>();
            final Object _lock = new Object();
            getContentInfo(content, s -> {
                Timber.v(">> Reader JS OK");
                imagesStr.set(s);
                synchronized (_lock) {
                    _lock.notifyAll();
                }
            }, quickDownload);
            synchronized (_lock) {
                Timber.w(">> Waiting for lock");
                try {
                    _lock.wait();
                } catch (InterruptedException e) {
                    Timber.w(e);
                }
            }
            Timber.w(">> Lock freed");
            List<ImageFile> result = new ArrayList<>();

            String jsResult = imagesStr.get().replace("\"[", "[").replace("]\"", "]").replace("\\\"", "\"");
            Timber.v(">> JSResult OK");
            try {
                List<String> imageUrls = JsonHelper.jsonToObject(jsResult, JsonHelper.LIST_STRINGS);
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    result.add(ImageFile.newCover(imageUrls.get(0), StatusContent.SAVED));
                    int order = 1;
                    for (String s : imageUrls)
                        result.add(ParseHelper.urlToImageFile(s, order++, imageUrls.size(), StatusContent.SAVED));
                }
            } catch (IOException e) {
                Timber.w(e);
            }
            content.setImageFiles(result);
 */

            return super.processContent(content, url, quickDownload);
        }
    }
}
