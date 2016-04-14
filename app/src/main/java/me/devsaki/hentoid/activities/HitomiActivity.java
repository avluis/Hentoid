package me.devsaki.hentoid.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.HitomiParser;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by Shiro on 1/20/2016.
 * Implements Hitomi.la source
 * <p/>
 * TODO: Re-implement without use of JavaScript:
 * Ref: http://goo.gl/UfIsZs
 */
public class HitomiActivity extends BaseWebActivity {
    private static final String TAG = LogHelper.makeLogTag(HitomiActivity.class);

    @SuppressLint("AddJavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setSite(Site.HITOMI);
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new HitomiWebViewClient());
        webView.addJavascriptInterface(new PageLoadListener(), "HTMLOUT");

        boolean bWebViewOverview = AndroidHelper.getWebViewOverviewPrefs();
        int webViewInitialZoom = AndroidHelper.getWebViewInitialZoomPrefs();

        if (bWebViewOverview) {
            webView.getSettings().setLoadWithOverviewMode(false);
            webView.setInitialScale(webViewInitialZoom);
            LogHelper.d(TAG, "WebView Initial Scale: " + webViewInitialZoom + "%");
        } else {
            webView.setInitialScale(ConstantsPreferences.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT);
            webView.getSettings().setLoadWithOverviewMode(true);
        }
    }

    private WebResourceResponse getJSWebResourceResponseFromAsset(String file) {
        String[] jsFiles = {"hitomi.js", "hitomi-horizontal.js", "hitomi-vertical.js"};
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";

        for (String jsFile : jsFiles) {
            if (file.contains(jsFile)) {
                String assetPath = pathPrefix + jsFile;
                try {
                    return getUtf8EncodedJSWebResourceResponse(getAssets().open(assetPath));
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
                LogHelper.d(TAG, "Malformed URL");
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