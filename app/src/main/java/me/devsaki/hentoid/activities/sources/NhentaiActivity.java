package me.devsaki.hentoid.activities.sources;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Map;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 */
public class NhentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "nhentai.net";
    private static final String[] GALLERY_FILTER = {"nhentai.net/g/", "nhentai.net/search/\\?q=[%0-9]+$"};
    private static final String[] RESULTS_FILTER = {"//nhentai.net[/]*$", "//nhentai.net/\\?", "//nhentai.net/search/\\?", "//nhentai.net/(character|artist|parody|tag|group)/"};
    private static final String[] BLOCKED_CONTENT = {"popunder"};
    private static final String[] DIRTY_ELEMENTS = {"section.advertisement"};

    Site getStartSite() {
        return Site.NHENTAI;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new CustomWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.setResultsUrlPatterns(RESULTS_FILTER);
        client.setResultUrlRewriter(this::rewriteResultsUrl);
        client.addDirtyElements(DIRTY_ELEMENTS);
        client.addContentBlockFilter(BLOCKED_CONTENT);
        return client;
    }

    private String rewriteResultsUrl(@NonNull Uri resultsUri, int page) {
        Uri.Builder builder = resultsUri.buildUpon();

        Map<String, String> params = HttpHelper.parseParameters(resultsUri);
        params.put("page", page + "");

        builder.clearQuery();
        for (Map.Entry<String, String> entry : params.entrySet())
            builder.appendQueryParameter(entry.getKey(), entry.getValue());

        return builder.toString();
    }
}
