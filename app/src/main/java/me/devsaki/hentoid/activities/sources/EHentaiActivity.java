package me.devsaki.hentoid.activities.sources;

import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.sources.EHentaiServer;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Implements E-Hentai source
 */
public class EHentaiActivity extends BaseWebActivity {

    private static final String[] DOMAIN_FILTER = {"e-hentai.org", "ehtracker.org"};
    private static final String[] GALLERY_FILTER = {"e-hentai.org/g/[0-9]+/[A-Za-z0-9\\-_]+"};

    Site getStartSite() {
        return Site.EHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new EHentaiWebClient(GALLERY_FILTER, this);
        CookieManager.getInstance().setCookie(Site.EHENTAI.getUrl(), "sl=dm_2");
        client.restrictTo(DOMAIN_FILTER);
        // E-h serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        return client;
    }

    private class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(String[] filter, WebContentListener listener) {
            super(filter, listener);
        }

        // We keep calling the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            String[] galleryUrlParts = urlStr.split("/");
            EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);
            compositeDisposable.add(EHentaiServer.EHENTAI_API.getGalleryMetadata(query, null)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata ->
                            {
                                isHtmlLoaded = true;
                                Content content = metadata.toContent(urlStr, Site.EHENTAI);

                                // Save cookies for future calls during download
                                Map<String, String> params = new HashMap<>();
                                for (Pair<String, String> p : HttpHelper.webResourceHeadersToOkHttpHeaders(requestHeaders, urlStr))
                                    if (p.first.equals(HttpHelper.HEADER_COOKIE_KEY))
                                        params.put(HttpHelper.HEADER_COOKIE_KEY, p.second);
                                content.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));

                                listener.onResultReady(content, quickDownload);
                            },
                            throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                isHtmlLoaded = true;
                                listener.onResultFailed();
                            })
            );
            return null;
        }
    }
}
