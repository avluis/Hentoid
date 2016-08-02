package me.devsaki.hentoid.activities;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.webkit.WebView;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.HentaiCafeParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;

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

    @Override
    void backgroundRequest(String extra) {
        LogHelper.d(TAG, extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(), extra);
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

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("//hentai.cafe/")) {
                Helper.executeAsyncTask(new HtmlLoader(), url);
            }
        }
    }

    private class HtmlLoader extends AsyncTask<String, Integer, Content> {
        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                processContent(HentaiCafeParser.parseContent(url));
            } catch (IOException e) {
                LogHelper.e(TAG, "Error parsing content: ", e);
            }

            return null;
        }
    }
}
