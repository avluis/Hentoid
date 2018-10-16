package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import androidx.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 *
 * NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
 */
public class NhentaiActivity extends BaseWebActivity {

    Site getStartSite() {
        return Site.NHENTAI;
    }

    @Override
    void setWebView(ObservableWebView webView) {
        NhentaiWebViewClient client = new NhentaiWebViewClient(this);
        client.restrictTo("nhentai.net");

        webView.setWebViewClient(client);
        super.setWebView(webView);
    }

    private static String getGalleryId(String url)
    {
        String[] parts = url.split("/");
        boolean gFound = false;
        for (String s : parts)
        {
            if (gFound)
            {
                return s;
            }
            if (s.equals("g")) gFound = true;
        }
        return "";
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {
        NhentaiWebViewClient(BaseWebActivity activity) {
            super(activity);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            BaseWebActivity activity = activityReference.get();

            if (url.contains("nhentai.net/g/") && activity != null) {
                executeAsyncTask(new JsonLoader(activity), "https://nhentai.net/api/gallery/"+getGalleryId(url));
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_style.css", TYPE.CSS);
            } else if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, url);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains("//static.nhentai.net/js/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_js.js", TYPE.JS);
            } else if (url.contains("//static.nhentai.net/css/")) {
                return getWebResourceResponseFromAsset(getStartSite(), "main_style.css", TYPE.CSS);
            } else if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }

    private static class JsonLoader extends AsyncTask<String, Integer, Content> {

        private final WeakReference<BaseWebActivity> activityReference;

        // only retain a weak reference to the activity
        JsonLoader(BaseWebActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            BaseWebActivity activity = activityReference.get();
            try {
                ContentParser parser = ContentParserFactory.getInstance().getParser(Site.NHENTAI);
                activity.processContent(parser.parseContent(HttpClientHelper.call(url)));
            } catch (Exception e) {
                Timber.e(e, "Error parsing content.");
                activity.runOnUiThread(() -> Helper.toast(HentoidApp.getAppContext(), R.string.web_unparsable));
            }

            return null;
        }
    }
}
