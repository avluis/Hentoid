package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.util.Pair;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.annimon.stream.function.Consumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hitomi.la";
    private static final String[] GALLERY_FILTER = {"//hitomi.la/[\\w%\\-]+/[^/]+-[0-9]{2,}.html(#[0-9]{1,2}){0,1}$"};
    private static final String[] RESULTS_FILTER = {"//hitomi.la[/]{0,1}$", "//hitomi.la[/]{0,1}\\?", "//hitomi.la/search.html", "//hitomi.la/index-[\\w%\\-\\.\\?]+", "//hitomi.la/(series|artist|tag|character)/[\\w%\\-\\.\\?]+"};
    private static final String[] BLOCKED_CONTENT = {"hitomi-horizontal.js", "hitomi-vertical.js", "invoke.js", "ion.sound"};
    private static final String[] JS_WHITELIST = {"galleries/[\\w%\\-]+.js$", "jquery", "filesaver", "common", "date", "download", "gallery", "jquery", "cookie", "jszip", "limitlists", "moment-with-locales", "moveimage", "pagination", "search", "searchlib", "yall", "reader", "decode_webp", "bootstrap", "gg.js", "languagesindex", "tagindex"};
    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder"};
    private static final String[] DIRTY_ELEMENTS = {".top-content > div:not(.list-title)", ".content div[class^=hitomi-]"};

    Site getStartSite() {
        return Site.HITOMI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        HitomiWebClient client = new HitomiWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addDirtyElements(DIRTY_ELEMENTS);
        client.setResultsUrlPatterns(RESULTS_FILTER);
        client.setResultUrlRewriter(this::rewriteResultsUrl);
        client.adBlocker.addToUrlBlacklist(BLOCKED_CONTENT);
        for (String s : JS_WHITELIST) client.adBlocker.addJsUrlPatternWhitelist(s);
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

    private void getImagesUrl(@NonNull Content onlineContent, @NonNull Consumer<String> listCallback) {
        // Get the gallery info file
        String galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + onlineContent.getUniqueSiteId() + ".js";

        // Get the gallery JSON
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, onlineContent.getReaderUrl()));

        if (extraProcessingDisposable != null)
            extraProcessingDisposable.dispose(); // Cancel whichever process was happening before

        extraProcessingDisposable = Single.fromCallable(() -> HttpHelper.getOnlineResource(galleryJsonUrl, headers, Site.HITOMI.useMobileAgent(), Site.HITOMI.useHentoidAgent(), Site.HITOMI.useWebviewAgent()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        response -> {
                            ResponseBody body = response.body();
                            if (null == body) throw new IOException("Empty body");

                            String galleryInfo = body.string();
//                            String galleryJson = body.string().replace("var galleryinfo = ", "");
//                            HitomiGalleryInfo gallery = JsonHelper.jsonToObject(galleryJson, HitomiGalleryInfo.class);
//                            HitomiGalleryInfo.HitomiGalleryPage firstPage = gallery.getFiles().get(0);
                            //webView.evaluateJavascript("(function() { " + getJsPagesScript(galleryInfo) + " })();", listCallback::accept);
                            webView.evaluateJavascript(getJsPagesScript(galleryInfo), listCallback::accept);
                        },
                        Timber::w
                );
    }

    // TODO optimize
    private String getJsPagesScript(@NonNull String galleryInfo) {
        StringBuilder sb = new StringBuilder();
        FileHelper.getAssetAsString(getAssets(), "hitomi_pages.js", sb);
        return sb.toString().replace("$galleryInfo", galleryInfo);
    }

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
            // TODO wait until the page's resources are all loaded ?
            Timber.i(">> not loading");
            while (!isLoading()) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Timber.w(e);
                    Thread.currentThread().interrupt();
                }
            }
            Timber.i(">> loading");
            while (isLoading()) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Timber.w(e);
                    Thread.currentThread().interrupt();
                }
            }
            Timber.i(">> done");
            getImagesUrl(content, l -> Timber.i(">> %s", l));
            return super.processContent(content, url, quickDownload);
        }
    }
}
