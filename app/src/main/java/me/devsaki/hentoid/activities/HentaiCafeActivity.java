package me.devsaki.hentoid.activities;

import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

/**
 * Created by avluis on 07/21/2016.
 * Implements Hentai Cafe source
 */
public class HentaiCafeActivity extends BaseWebActivity {
    private final static String TAG = LogHelper.makeLogTag(HentaiCafeActivity.class);

    @Override
    void setSite(Site site) {
        super.setSite(Site.HENTAICAFE);
    }

    @Override
    void setWebView(ObservableWebView webView) {
        webView.setWebViewClient(new HentaiCafeWebViewClient());

        super.setWebView(webView);
    }

    private class HentaiCafeWebViewClient extends CustomWebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("hentai.cafe"));
            } catch (MalformedURLException e) {
                LogHelper.d(TAG, "Malformed URL");
            }

            return false;
        }
    }
}
