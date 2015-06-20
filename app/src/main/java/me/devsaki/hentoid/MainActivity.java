package me.devsaki.hentoid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
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

import me.devsaki.hentoid.database.FakkuDroidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.Status;
import me.devsaki.hentoid.parser.FakkuParser;
import me.devsaki.hentoid.service.DownloadManagerService;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;
import com.melnykov.fab.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getName();

    public static final String INTENT_URL = "url";

    private FakkuDroidDB db;
    private Content currentContent;
    private FloatingActionButton fabRead, fabDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WebView webview = (WebView) findViewById(R.id.wbMain);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new CustomWebViewClient());
        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                ProgressBar pb = (ProgressBar) findViewById(R.id.pbMain);
                pb.setProgress(newProgress);
            }
        });
        webview.addJavascriptInterface(new FakkuLoadListener(), "HTMLOUT");
        String intentVar = getIntent().getStringExtra(INTENT_URL);
        webview.loadUrl(intentVar == null ? Constants.FAKKU_URL : intentVar);

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

        db = new FakkuDroidDB(MainActivity.this);

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
    }

    private void readContent() {
        if (currentContent != null) {
            currentContent = db.selectContentById(currentContent.getId());
            if (Status.DOWNLOADED == currentContent.getStatus() || Status.ERROR == currentContent.getStatus()) {
                AndroidHelper.openContent(currentContent, this);
            } else {
                fabRead.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void downloadContent() {
        currentContent = db.selectContentById(currentContent.getId());
        if (Status.DOWNLOADED == currentContent.getStatus()) {
            Toast.makeText(this, R.string.already_downloaded, Toast.LENGTH_SHORT).show();
            fabDownload.setVisibility(View.INVISIBLE);
            return;
        }
        Toast.makeText(this, R.string.in_queue, Toast.LENGTH_SHORT).show();
        currentContent.setDownloadDate(new Date().getTime());
        currentContent.setStatus(Status.DOWNLOADING);

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

    class CustomWebViewClient extends WebViewClient {

        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                URL u = new URL(url);
                if (u.getHost().equals("www.fakku.net")) {
                    return false;
                } else {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                    return true;
                }
            } catch (MalformedURLException e) {
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
            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                Log.i(TAG, "COOKIES ---- > " + cookies);
                java.net.CookieManager cookieManager = (java.net.CookieManager) CookieHandler.getDefault();
                if (cookieManager == null)
                    cookieManager = new java.net.CookieManager();
                cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
                CookieHandler.setDefault(cookieManager);
                String[] cookiesArray = cookies.split(";");
                URI fakkuCookie = new URI("https://fakku.net/");
                for (String cookie : cookiesArray) {
                    String key = cookie.split("=")[0].trim();
                    String value = cookie.split("=")[1].trim();
                    HttpCookie httpCookie = new HttpCookie(key, value);
                    httpCookie.setDomain("fakku.net");
                    httpCookie.setPath("/");
                    httpCookie.setVersion(0);
                    cookieManager.getCookieStore().removeAll();
                    cookieManager.getCookieStore().add(fakkuCookie, httpCookie);
                }
            } catch (Exception ex) {
                Log.e(TAG, "trying to get the cookies", ex);
            }

            URI uri = null;
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Error reading current url form webview", e);
            }

            if (uri != null && uri.getPath() != null) {
                String[] paths = uri.getPath().split("/");
                if (paths.length >= 3) {
                    if (paths[1].equals("doujinshi") || paths[1].equals("manga")) {
                        if (paths.length == 3 || !paths[3].equals("read")) {
                            try {
                                view.loadUrl("javascript:window.HTMLOUT.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                            } catch (Exception ex) {
                                Log.e(TAG, "Error executing javascript in webview", ex);
                            }
                        }
                    }
                } else if (paths.length == 2) {
                    String category = paths[1];
                    if (paths[1].equals("doujinshi") || paths[1].equals("manga"))
                        try {
                            String javascript = getString(R.string.hack_add_tags).replace("@category", category);
                            view.loadUrl(javascript);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error executing javascript in webview", ex);
                        }
                }
            }
        }
    }

    class FakkuLoadListener {

        @JavascriptInterface
        public void processHTML(String html) {
            if (html == null)
                return;
            Content content = FakkuParser.parseContent(html);
            if (content == null) {
                return;
            }
            Content contentbd = db.selectContentById(content.getUrl().hashCode());
            if (contentbd == null) {
                Log.i(TAG, "Saving content : " + content.getUrl());
                try {
                    content.setCoverImageUrl("http://" + content.getCoverImageUrl().substring(2));
                    db.insertContent(content);
                } catch (Exception e) {
                    Log.e(TAG, "Saving content", e);
                    return;
                }
            } else if (contentbd.getStatus() == Status.MIGRATED) {
                content.setStatus(Status.DOWNLOADED);
                db.insertContent(content);
                //Save JSON file
                try {
                    File dir = Helper.getDownloadDir(content.getFakkuId(), MainActivity.this);
                    Helper.saveJson(content, dir);
                } catch (IOException e) {
                    Log.e(TAG, "Error Save JSON " + content.getTitle(), e);
                }
            } else {
                content.setStatus(contentbd.getStatus());
            }
            if (content.isDownloadable() && content.getStatus() != Status.DOWNLOADED && content.getStatus() != Status.DOWNLOADING) {
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
            if (content.getStatus() == Status.DOWNLOADED || content.getStatus() == Status.ERROR) {
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
