package me.devsaki.hentoid.activities.sources;

import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.sources.ExHentaiServer;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Implements Ex-Hentai source
 */
public class ExHentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "exhentai.org";
    private static final String[] GALLERY_FILTER = {"exhentai.org/g/[0-9]+/[A-Za-z0-9\\-_]+"};

    // Store cookies in a member variable during onPageStarted
    // to avoid freezing the app when calling CookieManager.getInstance() during parseResponse
    // under Android 4.4 & 4.4.2
    private String exhCookiesStr = "";

    Site getStartSite() {
        return Site.EXHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new ExHentaiWebClient(GALLERY_FILTER, this);
        CookieManager.getInstance().setCookie(".exhentai.org", "sl=dm_2");
//        client.restrictTo(DOMAIN_FILTER);
        // ExH serves images through hosts that use http connections, which is detected as "mixed content" by the app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        return client;
    }

    private class ExHentaiWebClient extends CustomWebViewClient {

        ExHentaiWebClient(String[] filter, WebContentListener listener) {
            super(filter, listener);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.startsWith("https://exhentai.org")) {
                CookieManager mgr = CookieManager.getInstance();
                String existingCookiesStr = mgr.getCookie(".exhentai.org");
                if (existingCookiesStr != null && !existingCookiesStr.contains("ipb_member_id=")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mgr.removeAllCookies(null);
                    } else {
                        mgr.removeAllCookie();
                    }
                    webView.loadUrl("https://forums.e-hentai.org/index.php?act=Login&CODE=00/");
                } else {
                    exhCookiesStr = existingCookiesStr;
                }
            }

            if (url.startsWith("https://forums.e-hentai.org/index.php")) {
                CookieManager mgr = CookieManager.getInstance();
                String existingCookiesStr = mgr.getCookie(".e-hentai.org");
                if (existingCookiesStr != null && existingCookiesStr.contains("ipb_member_id="))
                    webView.loadUrl("https://exhentai.org/");
            }
        }


        // We keep calling the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            if (exhCookiesStr.isEmpty()) return null;

            String[] galleryUrlParts = urlStr.split("/");
            EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);
            compositeDisposable.add(ExHentaiServer.API.getGalleryMetadata(query, exhCookiesStr)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata ->
                            {
                                isHtmlLoaded = true;
                                Content content = metadata.toContent(urlStr);
                                Map<String, String> params = new HashMap<>();
                                params.put(HttpHelper.HEADER_COOKIE_KEY, exhCookiesStr);
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
