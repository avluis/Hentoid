package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.enums.Site;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentai.cafe";
    private static final String[] GALLERY_FILTER = {"//hentai.cafe/hc.fyi/[0-9]+$"};
    private static final String[] RESULTS_FILTER = {"//hentai.cafe[/]*$", "//hentai.cafe/\\?s=", "//hentai.cafe/page/", "//hentai.cafe/hc.fyi/(artist|tag)/"};

    Site getStartSite() {
        return Site.HENTAICAFE;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.setResultsUrlPatterns(RESULTS_FILTER);
        client.setResultUrlRewriter(this::rewriteResultsUrl);
        return client;
    }

    private String rewriteResultsUrl(@NonNull Uri resultsUri, int page) {
        List<String> pathSegments = new ArrayList<>(resultsUri.getPathSegments());
        Uri.Builder builder = resultsUri.buildUpon();
        if (pathSegments.contains("page")) { // Page already set
            int index = pathSegments.lastIndexOf("page");
            if (pathSegments.size() > index + 1) {
                pathSegments.set(index + 1, page + "");
                builder.path(TextUtils.join("/", pathSegments));
            } else {
                builder.appendPath(page + "");
            }
        } else { // Page not set
            builder.appendPath("page");
            builder.appendPath(page + "");
        }
        return builder.toString();
    }
}
