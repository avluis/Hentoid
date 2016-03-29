package me.devsaki.hentoid.activities;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseWebActivity;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.parser.HitomiParser;

/**
 * Created by Shiro on 1/20/2016.
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

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

            String js = getResources().getString(R.string.grab_html_from_webview);

            if (url.contains("//hitomi.la/galleries/")) {
                // following calls PageLoadListener.processHTML(*)
                // Conditional fixes issue with loadUrl("javascript:") on Android 4.4+
                if (Build.VERSION.SDK_INT >= 19) {
                    view.evaluateJavascript(js, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                            // Ignored - our js returns null
                        }
                    });
                } else {
                    view.loadUrl(js);
                }
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
