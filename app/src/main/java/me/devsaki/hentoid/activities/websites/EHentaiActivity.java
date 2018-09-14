package me.devsaki.hentoid.activities.websites;

import android.graphics.Bitmap;
import android.webkit.WebView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleriesMetadata;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.EHentaiServer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

/**
 * Created by Robb_w on 2018/04
 * Implements E-Hentai source
 */
public class EHentaiActivity extends BaseWebActivity {

    private static final String GALLERY_FILTER = "e-hentai.org/g/[0-9]+/[A-Za-z0-9\\-_]+";


    Site getStartSite() {
        return Site.EHENTAI;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        webClient = new EHentaiWebClient(this);
        webClient.restrictTo("e-hentai.org");

        webView.setWebViewClient(webClient);

        boolean bWebViewOverview = Preferences.getWebViewOverview();
        int webViewInitialZoom = Preferences.getWebViewInitialZoom();

        if (bWebViewOverview) {
            webView.getSettings().setLoadWithOverviewMode(false);
            webView.setInitialScale(webViewInitialZoom);
            Timber.d("WebView Initial Scale: %s%%", webViewInitialZoom);
        } else {
            webView.setInitialScale(Preferences.Default.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT);
            webView.getSettings().setLoadWithOverviewMode(true);
        }

        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class EHentaiWebClient extends CustomWebViewClient {

        EHentaiWebClient(BaseWebActivity activity) {
            super(activity);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            Pattern pattern = Pattern.compile(GALLERY_FILTER);
            Matcher matcher = pattern.matcher(url);

            if (matcher.find()) {
                String[] galleryUrlParts = url.split("/");
                EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4],galleryUrlParts[5]);
                disposable = EHentaiServer.API.getGalleryMetadata(query)
                        .observeOn(Schedulers.computation())
                        .subscribe(this::onContentSuccess, this::onContentFailed);

            }
        }

        private void onContentSuccess(EHentaiGalleriesMetadata metadata)
        {
            activity.processContent(metadata.toContent());
        }

        private void onContentFailed(Throwable t)
        {
            Timber.e(t, "Error parsing content.");
            activity.runOnUiThread(() -> Helper.toast(HentoidApp.getAppContext(), R.string.web_unparsable));
        }

    }
}
