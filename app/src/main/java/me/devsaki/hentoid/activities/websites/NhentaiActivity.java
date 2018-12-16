package me.devsaki.hentoid.activities.websites;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.util.HttpClientHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.TYPE;
import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 * <p>
 * NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
 */
public class NhentaiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "nhentai.net";
    private static final String GALLERY_FILTER = "nhentai.net/g/";

    Site getStartSite() {
        return Site.NHENTAI;
    }


    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new NhentaiWebViewClient(getStartSite(), this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private static String getGalleryId(String url) {
        String[] parts = url.split("/");
        boolean gFound = false;
        for (String s : parts) {
            if (gFound) {
                return s;
            }
            if (s.equals("g")) gFound = true;
        }
        return "";
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {

        NhentaiWebViewClient(Site startSite, ResultListener<Content> listener) {
            super(startSite, listener);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains(GALLERY_FILTER)) {
                executeAsyncTask(new JsonLoader(listener), "https://nhentai.net/api/gallery/" + getGalleryId(url));
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

        private final ResultListener<Content> listener;

        // only retain a weak reference to the activity
        JsonLoader(ResultListener<Content> listener) {
            this.listener = listener;
        }

        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                ContentParser parser = ContentParserFactory.getInstance().getParser(Site.NHENTAI);
                listener.onResultReady(parser.parseContent(HttpClientHelper.call(url)), 1);
            } catch (Exception e) {
                Timber.e(e, "Error parsing content.");
                listener.onResultFailed("");
            }

            return null;
        }
    }
}
