package me.devsaki.hentoid.activities.sources;

import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.EhentaiContent;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Implements E-Hentai source
 */
public class EHentaiActivity extends BaseWebActivity {

    private static final String[] DOMAIN_FILTER = {"e-hentai.org", "ehtracker.org"};
    private static final String[] GALLERY_FILTER = {"e-hentai.org/g/[0-9]+/[\\w\\-]+"};

    Site getStartSite() {
        return Site.EHENTAI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new EHentaiWebClient(getStartSite(), GALLERY_FILTER, this);
        CookieManager.getInstance().setCookie(Site.EHENTAI.getUrl(), "sl=dm_2"); // Show thumbs in results page ("extended display")
        CookieManager.getInstance().setCookie(Site.EHENTAI.getUrl(), "nw=1"); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        client.restrictTo(DOMAIN_FILTER);
        // E-h serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        return client;
    }

    private static class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        // We call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            activity.onGalleryPageStarted();

            ContentParser contentParser = new EhentaiContent();
            compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            content -> super.processContent(content, urlStr, quickDownload),
                            Timber::w
                    )
            );

            if (isMarkDownloaded())
                return super.parseResponse(urlStr, requestHeaders, false, false); // Rewrite HTML
            else return null;
        }
    }
}
