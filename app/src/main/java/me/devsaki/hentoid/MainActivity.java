package me.devsaki.hentoid;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

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
import me.devsaki.hentoid.parser.FakkuParser;
import me.devsaki.hentoid.parser.HitomiParser;
import me.devsaki.hentoid.parser.NhentaiParser;
import me.devsaki.hentoid.parser.PururinParser;
import me.devsaki.hentoid.service.DownloadManagerService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;

public class MainActivity extends AppCompatActivity {

    public static final String INTENT_URL = "url";
    public static final String INTENT_SITE = "site";
    private static final String TAG = MainActivity.class.getName();
    private HentoidDB db;
    private Content currentContent;
    private Site site;
    private FloatingActionButton fabRead, fabDownload;
    private SwipeRefreshLayout swipeLayout;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WebView webview = (WebView) findViewById(R.id.wbMain);

        webview.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });

        webview.setLongClickable(false);
        webview.setHapticFeedbackEnabled(false);
        webview.setWebViewClient(new CustomWebViewClient());
        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                ProgressBar pb = (ProgressBar) findViewById(R.id.pbMain);
                pb.setProgress(newProgress);
                pb.setVisibility(View.VISIBLE);
            }
        });
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setDisplayZoomControls(false);
        webview.getSettings().setUserAgentString(Constants.USER_AGENT);
        webview.getSettings().setDomStorageEnabled(true);
        webview.getSettings().setUseWideViewPort(true);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setInitialScale(50);
        webview.addJavascriptInterface(new PageLoadListener(), "HTMLOUT");

        String intentVar = getIntent().getStringExtra(INTENT_URL);
        site = Site.searchByCode(getIntent().getIntExtra(INTENT_SITE, Site.FAKKU.getCode()));

        if (site != null) {
            webview.loadUrl(intentVar == null ? site.getUrl() : intentVar);
        }

        fabRead = (FloatingActionButton) findViewById(R.id.fabRead);
        fabRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readContent();
            }
        });
        fabRead.setVisibility(View.INVISIBLE);

        fabDownload = (FloatingActionButton) findViewById(R.id.fabDownload);
        fabDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadContent();
            }
        });
        fabDownload.setVisibility(View.INVISIBLE);

        db = new HentoidDB(MainActivity.this);

        FloatingActionButton fabDownloads = (FloatingActionButton) findViewById(R.id.fabDownloads);
        fabDownloads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent mainActivity = new Intent(MainActivity.this, DownloadsActivity.class);
                startActivity(mainActivity);
            }
        });
        FloatingActionButton fabRefresh = (FloatingActionButton) findViewById(R.id.fabRefresh);
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webview.reload();
            }
        });

        // Swipe down to refresh webView.
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
                webview.reload();
            }
        });
        swipeLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    private void readContent() {
        if (currentContent != null) {
            currentContent = db.selectContentById(currentContent.getId());
            if (StatusContent.DOWNLOADED == currentContent.getStatus() || StatusContent.ERROR == currentContent.getStatus()) {
                AndroidHelper.openContent(currentContent, this);
            } else {
                fabRead.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void downloadContent() {
        currentContent = db.selectContentById(currentContent.getId());
        if (StatusContent.DOWNLOADED == currentContent.getStatus()) {
            Toast.makeText(this, R.string.already_downloaded, Toast.LENGTH_SHORT).show();
            fabDownload.setVisibility(View.INVISIBLE);
            return;
        }
        Toast.makeText(this, R.string.in_queue, Toast.LENGTH_SHORT).show();
        currentContent.setDownloadDate(new Date().getTime());
        currentContent.setStatus(StatusContent.DOWNLOADING);

        db.updateContentStatus(currentContent);
        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, DownloadManagerService.class);
        startService(intent);
        fabDownload.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    WebView webview = (WebView) findViewById(R.id.wbMain);
                    if (webview.canGoBack()) {
                        webview.goBack();
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
            fabDownload.setVisibility(View.INVISIBLE);
            fabRead.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {

            URI uri = null;
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Error reading current url form webview", e);
            }

            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                java.net.CookieManager cookieManager = (java.net.CookieManager) CookieHandler.getDefault();
                if (cookieManager == null)
                    cookieManager = new java.net.CookieManager();
                cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
                CookieHandler.setDefault(cookieManager);
                if (cookies != null) {
                    String[] cookiesArray = cookies.split(";");
                    for (String cookie : cookiesArray) {
                        String key = cookie.split("=")[0].trim();
                        if (key.equals("cf_clearance") || site != Site.PURURIN) {
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
                Log.e(TAG, "trying to get the cookies", ex);
            }

            if (uri != null && uri.getPath() != null) {
                String[] paths = uri.getPath().split("/");
                if ((site == Site.HITOMI) &&
                        paths.length > 1 && paths[1].startsWith("galleries")) {
                    try {
                        view.loadUrl("javascript:window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                    } catch (Exception ex) {
                        Log.e(TAG, "Error executing javascript in webview", ex);
                    }
                } else if ((site == Site.NHENTAI) &&
                        paths.length > 1 && paths[1].startsWith("g")) {
                    try {
                        view.loadUrl("javascript:window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                    } catch (Exception ex) {
                        Log.e(TAG, "Error executing javascript in webview", ex);
                    }
                }
            }
        }
    }

    private class PageLoadListener {

        @JavascriptInterface
        public void processHTML(String html) {
            if (html == null)
                return;

            Content content = null;
            if (site == Site.HITOMI)
                content = HitomiParser.parseContent(html);
            else if (site == Site.NHENTAI)
                content = NhentaiParser.parseContent(html);

            if (content == null) {
                return;
            }
            Content contentDB = db.selectContentById(content.getUrl().hashCode());
            if (contentDB != null) {
                content.setStatus(contentDB.getStatus());
                content.setImageFiles(contentDB.getImageFiles());
                content.setDownloadDate(contentDB.getDownloadDate());
            }
            db.insertContent(content);

            if (content.isDownloadable() && content.getStatus() != StatusContent.DOWNLOADED && content.getStatus() != StatusContent.DOWNLOADING) {
                currentContent = content;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fabDownload.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fabDownload.setVisibility(View.INVISIBLE);
                    }
                });
            }
            if (content.getStatus() == StatusContent.DOWNLOADED || content.getStatus() == StatusContent.ERROR) {
                currentContent = content;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fabRead.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fabRead.setVisibility(View.INVISIBLE);
                    }
                });
            }
        }
    }
}
