package me.devsaki.hentoid.activities;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 * <p/>
 * TODO: Implement Pop-Up/Ad filters
 */
public class TsuminoActivity extends BaseWebActivity {
    private static final String TAG = LogHelper.makeLogTag(TsuminoActivity.class);

    private boolean downloadFabPressed = false;
    private int historyIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setSite(Site.TSUMINO);
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new TsuminoWebViewClient());
    }

    @SuppressWarnings("UnusedParameters")
    @Override
    public void onDownloadFabClick(View view) {
        downloadFabPressed = true;
        historyIndex = webView.copyBackForwardList().getCurrentIndex();

        String newUrl = webView.getUrl().replace("Book/Info", "Read/View");
        final int index = Helper.ordinalIndexOf(newUrl, '/', 5);
        if (index > 0) newUrl = newUrl.substring(0, index);
        webView.loadUrl(newUrl);
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("tsumino.com"));
            } catch (MalformedURLException e) {
                LogHelper.d(TAG, "Malformed URL");
            }

            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (downloadFabPressed &&
                    !(url.contains("//www.tsumino.com/Read/View/") ||
                            url.contains("//www.tsumino.com/Read/Auth/") ||
                            url.contains("//www.tsumino.com/Read/AuthProcess"))) {
                downloadFabPressed = false;
            }

            if (url.contains("//www.tsumino.com/Book/Info/")) {
                AndroidHelper.executeAsyncTask(new HtmlLoader(), url);
            } else if (downloadFabPressed && url.contains("//www.tsumino.com/Read/View/")) {
                downloadFabPressed = false;
                int currentIndex = webView.copyBackForwardList().getCurrentIndex();
                webView.goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }
    }

    private class HtmlLoader extends AsyncTask<String, Integer, Content> {
        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                processContent(TsuminoParser.parseContent(url));
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}