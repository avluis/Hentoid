package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.webkit.WebResourceResponse;

import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;

import static me.devsaki.hentoid.enums.Site.HENTAICAFE;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hentai.cafe";
    private static final String GALLERY_FILTER = "//hentai.cafe/[^/]+/$";

    Site getStartSite() {
        return Site.HENTAICAFE;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new HentaiCafeWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class HentaiCafeWebViewClient extends CustomWebViewClient {

        HentaiCafeWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> headers) {
            if (urlStr.startsWith(HENTAICAFE.getUrl() + "/78-2/")          // ignore tags page
                    || urlStr.startsWith(HENTAICAFE.getUrl() + "/artists/")    // ignore artist page
                    || urlStr.startsWith(HENTAICAFE.getUrl() + "/?s=")         // ignore text search results
            ) {
                return null;
            }
            return super.parseResponse(urlStr, headers);
        }
    }
}
