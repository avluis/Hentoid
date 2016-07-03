package me.devsaki.hentoid.activities;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.views.ObservableWebView;

/**
 * Created by Shiro on 1/22/2016.
 * Implements tsumino source
 */
public class TsuminoActivity extends BaseWebActivity {
    private static final String TAG = LogHelper.makeLogTag(TsuminoActivity.class);

    private boolean downloadFabPressed = false;
    private int historyIndex;

    private static int ordinalIndexOf(String str) {
        int i = 5;
        int pos = str.indexOf('/', 0);
        while (i-- > 0 && pos != -1) {
            pos = str.indexOf('/', pos + 1);
        }

        return pos;
    }

    @Override
    void setSite(Site site) {
        super.setSite(Site.TSUMINO);
    }

    @Override
    void setWebView(ObservableWebView webView) {
        webView.setWebViewClient(new TsuminoWebViewClient());

        super.setWebView(webView);
    }

    private WebResourceResponse getJSWebResourceResponseFromAsset() {
        String pathPrefix = getSite().getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + "no.js";
        try {
            return getUtf8EncodedJSWebResourceResponse(getAssets().open(file));
        } catch (IOException e) {
            return null;
        }
    }

    private WebResourceResponse getUtf8EncodedJSWebResourceResponse(InputStream open) {
        return new WebResourceResponse("text/js", "UTF-8", open);
    }

    @SuppressWarnings("UnusedParameters")
    @Override
    public void onDownloadFabClick(View view) {
        downloadFabPressed = true;
        historyIndex = getWebView().copyBackForwardList().getCurrentIndex();

        String newUrl = getWebView().getUrl().replace("Book/Info", "Read/View");
        final int index = ordinalIndexOf(newUrl);
        if (index > 0) newUrl = newUrl.substring(0, index);
        getWebView().loadUrl(newUrl);
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
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            if (url.contains("//www.tsumino.com/Book/Info/")) {
                Helper.executeAsyncTask(new HtmlLoader(), url);
            } else if (downloadFabPressed && url.contains("//www.tsumino.com/Read/View/")) {
                downloadFabPressed = false;
                int currentIndex = getWebView().copyBackForwardList().getCurrentIndex();
                getWebView().goBackOrForward(historyIndex - currentIndex);
                processDownload();
            }
        }

        @SuppressWarnings("deprecation") // From API 21 we should use another overload
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("pop.js")) {
                return getJSWebResourceResponseFromAsset();
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            if (request.getUrl().toString().contains("pop.js")) {
                return getJSWebResourceResponseFromAsset();
            } else {
                return super.shouldInterceptRequest(view, request);
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
