package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.util.Map;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Created by Shiro on 1/20/2016.
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hitomi.la";
    private static final String[] GALLERY_FILTER = {"//hitomi.la/[\\w%\\-]+/[^/]+-[0-9]{2,}.html(#[0-9]{1,2}){0,1}$"};
    private static final String[] RESULTS_FILTER = {"//hitomi.la[/]{0,1}$", "//hitomi.la[/]{0,1}\\?", "//hitomi.la/search.html", "//hitomi.la/index-[\\w%\\-\\.\\?]+", "//hitomi.la/(series|artist|tag|character)/[\\w%\\-\\.\\?]+"};
    private static final String[] BLOCKED_CONTENT = {"hitomi-horizontal.js", "hitomi-vertical.js", "invoke.js", "ion.sound"};
    private static final String[] JS_WHITELIST = {"galleries/[\\w%\\-]+.js$", "jquery", "filesaver", "common", "date", "download", "gallery", "jquery", "cookie", "jszip", "limitlists", "moment-with-locales", "moveimage", "pagination", "search", "searchlib", "yall", "reader", "decode_webp", "bootstrap"};
    private static final String[] JS_CONTENT_BLACKLIST = {"exoloader", "popunder"};
    private static final String[] DIRTY_ELEMENTS = {".top-content > div:not(.list-title)"};

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

    private static class HitomiWebClient extends CustomWebViewClient {

        HitomiWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (url.contains("galleryblock")) // Process book blocks to mark existing ones
                return parseResponse(url, request.getRequestHeaders(), false, false);

            return super.shouldInterceptRequest(view, request);
        }
    }
}
