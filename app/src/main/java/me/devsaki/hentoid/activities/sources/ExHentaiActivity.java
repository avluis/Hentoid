package me.devsaki.hentoid.activities.sources;

import android.graphics.Bitmap;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StorageLocation;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.ExhentaiContent;
import me.devsaki.hentoid.parsers.images.EHentaiParser;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.file.FileHelper;
import timber.log.Timber;

/**
 * Implements Ex-Hentai source
 */
public class ExHentaiActivity extends BaseWebActivity {

    private static final String[] GALLERY_FILTER = {"exhentai.org/g/[0-9]+/[\\w\\-]+"};
    private static final String DOMAIN = ".exhentai.org";

    Site getStartSite() {
        return Site.EXHENTAI;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        CustomWebViewClient client = new ExHentaiWebClient(getStartSite(), GALLERY_FILTER, this);
        CookieManager.getInstance().setCookie(DOMAIN, "sl=dm_2");  // Show thumbs in results page ("extended display")
        CookieManager.getInstance().setCookie(DOMAIN, "nw=1"); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        // ExH serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        return client;
    }

    private class ExHentaiWebClient extends CustomWebViewClient {

        ExHentaiWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            EHentaiParser.EhAuthState authState = EHentaiParser.getAuthState(url);
            if (url.startsWith("https://exhentai.org") && authState != EHentaiParser.EhAuthState.LOGGED) {
                CookieManager.getInstance().removeAllCookies(null);
                webView.loadUrl("https://forums.e-hentai.org/index.php?act=Login&CODE=00/");
                if (authState == EHentaiParser.EhAuthState.UNLOGGED_ABNORMAL)
                    showTooltip(R.string.help_web_incomplete_exh_credentials, true);
                else
                    showTooltip(R.string.help_web_invalid_exh_credentials, true);
            }

            if (url.startsWith("https://forums.e-hentai.org/index.php") && authState == EHentaiParser.EhAuthState.LOGGED) {
                webView.loadUrl("https://exhentai.org/");
            }

            showTooltip(R.string.help_web_exh_account, false);
        }

        private void logCookies(@NonNull final String prefix, @NonNull final String cookieStr) {
            try {
                DocumentFile root = FileHelper.getDocumentFromTreeUriString(getApplication(), Preferences.getStorageUri(StorageLocation.PRIMARY_1));
                if (root != null) {
                    DocumentFile cookiesLog = FileHelper.findOrCreateDocumentFile(getApplication(), root, "text/plain", "cookies_" + prefix + "_log.txt");
                    if (cookiesLog != null)
                        FileHelper.saveBinary(getApplication(), cookiesLog.getUri(), cookieStr.getBytes());
                }
            } catch (IOException e) {
                Timber.e(e);
            }
        }

        // We call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            if (analyzeForDownload || quickDownload) {
                if (activity != null) activity.onGalleryPageStarted();

                ContentParser contentParser = new ExhentaiContent();
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
