package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.os.Build;
import androidx.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by avluis on 07/21/2016.
 * Implements ASMHentai source
 */
public class ASMHentaiActivity extends BaseWebActivity {

    private static final String[] blockedContent = {"f.js"};

    Site getStartSite() {
        return Site.ASMHENTAI;
    }


    @Override
    void setWebView(ObservableWebView webView) {
        ASMViewClient client = new ASMViewClient(this, "asmhentai.com/g/");
        client.restrictTo("asmhentai.com");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    @Override
    void backgroundRequest(String extra) {
        Timber.d(extra);
        Helper.toast("Processing...");
        executeAsyncTask(new HtmlLoader(this), extra);
    }

    private class ASMViewClient extends CustomWebViewClient {

        ASMViewClient(BaseWebActivity activity, String filteredUrl) {
            super(activity, filteredUrl);
            addContentBlockFilter(blockedContent);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else if (url.contains("main.js")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main.js", TYPE.JS);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
