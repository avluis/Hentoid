package me.devsaki.hentoid.activities.sources;

import android.graphics.Bitmap;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.EhentaiContent;
import me.devsaki.hentoid.parsers.images.EHentaiParser;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Implements E-Hentai source
 */
public class EHentaiActivity extends BaseWebActivity {

    private static final String[] DOMAIN_FILTER = {"e-hentai.org", "ehtracker.org"};
    private static final String[] GALLERY_FILTER = {"e-hentai.org/g/[0-9]+/[\\w\\-]+"};

    Site getStartSite() {
        return Site.EHENTAI;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new EHentaiWebClient(getStartSite(), GALLERY_FILTER, this);
        CookieManager.getInstance().setCookie(Site.EHENTAI.getUrl(), "sl=dm_2"); // Show thumbs in results page ("extended display")
        CookieManager.getInstance().setCookie(Site.EHENTAI.getUrl(), "nw=1"); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        client.restrictTo(DOMAIN_FILTER);
        // E-h serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        return client;
    }

    private class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            EHentaiParser.EhAuthState authState = EHentaiParser.getAuthState(url);
            if (Preferences.isDownloadEhHires() && authState != EHentaiParser.EhAuthState.LOGGED && !url.startsWith("https://forums.e-hentai.org/index.php")) {
                webView.loadUrl("https://forums.e-hentai.org/index.php?act=Login&CODE=00/");
                showTooltip(R.string.help_web_hires_eh_account, true);
            }
        }

        // We call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            if (analyzeForDownload || quickDownload) {
                if (activity != null) activity.onGalleryPageStarted();

                ContentParser contentParser = new EhentaiContent();
                compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.computation())
                        .map(content -> super.processContent(content, urlStr, quickDownload))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                content2 -> resConsumer.onResultReady(content2, quickDownload),
                                Timber::w
                        )
                );
            }

            if (isMarkDownloaded() || isMarkMerged())
                return super.parseResponse(urlStr, requestHeaders, false, false); // Rewrite HTML
            else return null;
        }
    }
}
