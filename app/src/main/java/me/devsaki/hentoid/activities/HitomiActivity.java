package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseWebActivity;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.parser.HitomiParser;

/**
 * Created by Shiro on 1/20/2016.
 * TODO: Re-implement as Activity ->> Fragment.
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

    private WebResourceResponse getJSWebResourceResponseFromAsset(String file) {

        String[] jsFiles = {
                "hitomi.js", "hitomi-horizontal.js", "hitomi-vertical.js"};

        for (String jsFile : jsFiles) {
            if (file.contains(jsFile)) {
                try {
                    return getUtf8EncodedJSWebResourceResponse(getAssets().open(jsFile));
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private WebResourceResponse getUtf8EncodedJSWebResourceResponse(InputStream open) {
        return new WebResourceResponse("text/js", "UTF-8", open);
    }

    private class HitomiWebViewClient extends CustomWebViewClient {

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains(".js")) {
                return getJSWebResourceResponseFromAsset(url);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            if (request.getUrl().toString().contains(".js")) {
                return getJSWebResourceResponseFromAsset(request.getUrl().toString());
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }

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
