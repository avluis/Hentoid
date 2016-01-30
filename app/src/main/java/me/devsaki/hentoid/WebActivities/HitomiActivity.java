package me.devsaki.hentoid.WebActivities;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.parser.HitomiParser;

/**
 * Created by Shiro on 1/20/2016.
 * subclassed contents from MainActivity written by csaki
 */
public class HitomiActivity extends WebActivity {

    private static final String TAG = HitomiActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setSite(Site.HITOMI);
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new HitomiWebViewClient());
        webView.setInitialScale(20);
        webView.addJavascriptInterface(new PageLoadListener(), "HTMLOUT");
    }

    private class HitomiWebViewClient extends CustomWebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("hitomi.la"));
            } catch (MalformedURLException e) {
                Log.d(TAG, "Malformed URL");
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            webView.loadUrl(getResources().getString(R.string.remove_js_css));
            webView.loadUrl(getResources().getString(R.string.restore_hitomi_js));
            if (url.contains("//hitomi.la/galleries/")) {
                // following line calls PageLoadListener.processHTML(*)
                view.loadUrl(getResources().getString(R.string.grab_html_from_webview));
            }
        }
    }

    private class PageLoadListener {
        @JavascriptInterface
        public void processHTML(String html) {
            if (html == null) {
                return;
            }
            processContent(HitomiParser.parseContent(html));
        }
    }
}
