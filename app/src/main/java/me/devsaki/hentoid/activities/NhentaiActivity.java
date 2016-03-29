package me.devsaki.hentoid.activities;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import java.net.MalformedURLException;
import java.net.URL;

import me.devsaki.hentoid.abstracts.BaseWebActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.HttpClientHelper;

/**
 * Created by Shiro on 1/20/2016.
 * Implements nhentai source
 */
public class NhentaiActivity extends BaseWebActivity {

    private static final String TAG = NhentaiActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setSite(Site.NHENTAI);
        super.onCreate(savedInstanceState);

        webView.setWebViewClient(new NhentaiWebViewClient());
    }

    private class NhentaiWebViewClient extends CustomWebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("nhentai.net"));
            } catch (MalformedURLException e) {
                Log.d(TAG, "Malformed URL");
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            if (url.contains("//nhentai.net/g/")) {
                AndroidHelper.executeAsyncTask(new LoaderJson(), url + "json");
            }
        }
    }

    private class LoaderJson extends AsyncTask<String, Integer, Content> {
        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            try {
                processContent(NhentaiParser.parseContent(HttpClientHelper.call(url)));
            } catch (Exception e) {
                Log.e(TAG, "Error parsing nhentai json: " + url, e);
            }
            return null;
        }
    }
}
