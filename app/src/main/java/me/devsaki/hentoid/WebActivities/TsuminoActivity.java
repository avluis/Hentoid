package me.devsaki.hentoid.WebActivities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebBackForwardList;
import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.parser.TsuminoParser;
import me.devsaki.hentoid.util.Helper;

/**
 * Created by Shiro on 1/22/2016.
 * TODO: Re-implement as Activity ->> Fragment.
 */
public class TsuminoActivity extends WebActivity {

    private static final String TAG = TsuminoActivity.class.getName();
    private boolean downloadFabPressed = false;
    private int historyIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setSite(Site.TSUMINO);
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new TsuminoWebViewClient());
        webView.addJavascriptInterface(new PageLoadListener(), "HTMLOUT");
    }

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
                Log.d(TAG, "Malformed URL");
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (downloadFabPressed
                    && !(url.contains("//www.tsumino.com/Read/View/")
                    || url.contains("//www.tsumino.com/Read/Auth/")
                    || url.contains("//www.tsumino.com/Read/AuthProcess"))) {
                downloadFabPressed = false;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    String[] cookiesArray = cookies.split(";");
                    for (String cookie : cookiesArray) {
                        if (cookie.contains("Tsumino_Web")) {
                            Helper.setSessionCookie(cookie);
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "Error trying to get the cookies", ex);
            }

            if (url.contains("//www.tsumino.com/Book/Info/")) {
                // following line calls PageLoadListener.processHTML(*)
                view.loadUrl(getResources().getString(R.string.grab_html_from_webview));
            } else if (url.contains("//www.tsumino.com/Read/View/")
                    && downloadFabPressed) {
                downloadFabPressed = false;

                int currentIndex = webView.copyBackForwardList().getCurrentIndex();
                webView.goBackOrForward(historyIndex - currentIndex);
                processDownload();
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
