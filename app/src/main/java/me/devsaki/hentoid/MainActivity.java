package me.devsaki.hentoid;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.parser.HitomiParser;
import me.devsaki.hentoid.parser.NhentaiParser;
import me.devsaki.hentoid.service.DownloadManagerService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.HttpClientHelper;

public class MainActivity extends AppCompatActivity {

    public static final String INTENT_URL = "url";
    public static final String INTENT_SITE = "site";
    private static final String TAG = MainActivity.class.getName();
    private HentoidDB db;
    private Content currentContent;
    private Site site;
    private WebView webView;
    private FloatingActionButton fabRead, fabDownload;
    private SwipeRefreshLayout swipeLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        site = Site.searchByCode(getIntent().getIntExtra(INTENT_SITE, -1));
        db = new HentoidDB(this);
        webView = (WebView) findViewById(R.id.wbMain);
        fabRead = (FloatingActionButton) findViewById(R.id.fabRead);
        fabDownload = (FloatingActionButton) findViewById(R.id.fabDownload);

        fabRead.hide();
        fabDownload.hide();

        initWebView();
        initSwipeLayout();

        String intentVar = getIntent().getStringExtra(INTENT_URL);
        if (site != null) {
            webView.loadUrl(intentVar == null ? site.getUrl() : intentVar);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView() {
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        webView.setLongClickable(false);
        webView.setHapticFeedbackEnabled(false);
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);

                if (newProgress == 100) {
                    swipeLayout.setRefreshing(false);
                } else {
                    swipeLayout.setRefreshing(true);
                }
            }
        });
        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setUserAgentString(Constants.USER_AGENT);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);

        webView.setInitialScale(20);
        webView.addJavascriptInterface(new PageLoadListener(), "HTMLOUT");
    }

    private void initSwipeLayout() {
        swipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        swipeLayout.setRefreshing(false);
                    }
                }, 5000);
                webView.reload();
            }
        });
        swipeLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    public void refreshWebView(View view) {
        webView.reload();
    }

    public void closeActivity(View view) {
        Intent mainActivity = new Intent(MainActivity.this, DownloadsActivity.class);
        startActivity(mainActivity);
    }

    public void readContent(View view) {
        if (currentContent != null) {
            currentContent = db.selectContentById(currentContent.getId());
            if (StatusContent.DOWNLOADED == currentContent.getStatus()
                    || StatusContent.ERROR == currentContent.getStatus()) {
                AndroidHelper.openContent(currentContent, this);
            } else {
                fabRead.hide();
            }
        }
    }

    public void downloadContent(View view) {
        currentContent = db.selectContentById(currentContent.getId());
        if (StatusContent.DOWNLOADED == currentContent.getStatus()) {
            Toast.makeText(this, R.string.already_downloaded, Toast.LENGTH_SHORT).show();
            fabDownload.hide();
            return;
        }
        Toast.makeText(this, R.string.in_queue, Toast.LENGTH_SHORT).show();
        currentContent.setDownloadDate(new Date().getTime());
        currentContent.setStatus(StatusContent.DOWNLOADING);

        db.updateContentStatus(currentContent);
        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, DownloadManagerService.class);
        startService(intent);
        fabDownload.hide();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

    private class CustomWebViewClient extends WebViewClient {

        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                return !(u.getHost().endsWith("hitomi.la") && site == Site.HITOMI) &&
                        !(u.getHost().endsWith("nhentai.net") && site == Site.NHENTAI);
            } catch (MalformedURLException e) {
                Log.d(TAG, "Malformed URL");
            }
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {

            fabDownload.hide();
            fabRead.hide();

            if ((site == Site.NHENTAI) && url.contains("//nhentai.net/g/")) {
                AndroidHelper.executeAsyncTask(new LoaderJson(), url + "json");
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {

            URI uri = null;
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Error reading current url from webview", e);
            }

            if (site == Site.HITOMI) {
                webView.loadUrl(getResources().getString(R.string.remove_js_css));
                webView.loadUrl(getResources().getString(R.string.restore_hitomi_js));
            }

            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                java.net.CookieManager cookieManager = (java.net.CookieManager)
                        CookieHandler.getDefault();
                if (cookieManager == null)
                    cookieManager = new java.net.CookieManager();
                cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
                CookieHandler.setDefault(cookieManager);
                if (cookies != null) {
                    String[] cookiesArray = cookies.split(";");
                    for (String cookie : cookiesArray) {
                        String key = cookie.split("=")[0].trim();
                        if (key.equals("cf_clearance")) {
                            String value = cookie.split("=")[1].trim();
                            HttpCookie httpCookie = new HttpCookie(key, value);
                            if (uri != null) {
                                httpCookie.setDomain(uri.getHost());
                            }
                            httpCookie.setPath("/");
                            httpCookie.setVersion(0);
                            cookieManager.getCookieStore().add(uri, httpCookie);
                        }
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "Error trying to get the cookies", ex);
            }

            if ((site == Site.HITOMI) && url.contains("//hitomi.la/galleries/")) {
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

    private void processContent(Content content) {
        if (content == null)
            return;

        Content contentDB = db.selectContentById(content.getUrl().hashCode());
        if (contentDB != null) {
            content.setStatus(contentDB.getStatus());
            content.setImageFiles(contentDB.getImageFiles());
            content.setDownloadDate(contentDB.getDownloadDate());
        }
        db.insertContent(content);

        if (content.isDownloadable() && content.getStatus() != StatusContent.DOWNLOADED
                && content.getStatus() != StatusContent.DOWNLOADING) {
            currentContent = content;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fabDownload.show();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fabDownload.hide();
                }
            });
        }
        if (content.getStatus() == StatusContent.DOWNLOADED
                || content.getStatus() == StatusContent.ERROR) {
            currentContent = content;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fabRead.show();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fabRead.hide();
                }
            });
        }
    }
}