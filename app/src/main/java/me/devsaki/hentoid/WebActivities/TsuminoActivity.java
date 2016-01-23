package me.devsaki.hentoid.WebActivities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebBackForwardList;
import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.parser.TsuminoParser;

/**
 * Created by Shiro on 1/22/2016.
 */
public class TsuminoActivity  extends WebActivity {

    private static final String TAG = TsuminoActivity.class.getName();
    private String currentUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        site = Site.TSUMINO;
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new TsuminoWebViewClient());
        webView.addJavascriptInterface(new PageLoadListener(), "HTMLOUT");
    }

    @Override
    public void onDownloadFabClick(View view) {
        if (currentUrl.contains("//www.tsumino.com/Book/Info/")) {
            String newUrl = currentUrl
                    .replace("/Book/Info/", "/Read/View/")
                    .substring(0, currentUrl.lastIndexOf('/'));
            webView.loadUrl(newUrl);
        }
    }

    private class TsuminoWebViewClient extends CustomWebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("tsumino.com"));
            } catch (MalformedURLException e) {
                Log.d(TAG, "Malformed URL");
            }
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            currentUrl = url;

            if (url.contains("//www.tsumino.com/Book/Info/")) {
                // following line calls PageLoadListener.processHTML(*)
                view.loadUrl(getResources().getString(R.string.grab_html_from_webview));
            } else if (url.contains("//www.tsumino.com/Read/View/")) {
                WebBackForwardList history = webView.copyBackForwardList();
                int i = history.getCurrentIndex();
                String historyUrl;
                do {
                    historyUrl = history.getItemAtIndex(--i).getUrl();
                } while (!historyUrl.contains("//www.tsumino.com/Book/Info/"));

                webView.goBackOrForward(i - history.getCurrentIndex());
            }
        }
    }

    private class PageLoadListener {
        @JavascriptInterface
        public void processHTML(String html) {
            if (html == null) {
                return;
            }
            processContent(TsuminoParser.parseContent(html));
        }
    }
}
