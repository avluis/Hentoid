package me.devsaki.hentoid.activities.websites;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.services.ContentDownloadService;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.executeAsyncTask;
import static me.devsaki.hentoid.util.Helper.getWebResourceResponseFromAsset;

/**
 * Browser activity which allows the user to navigate a supported source.
 * No particular source should be filtered/defined here.
 * The source itself should contain every method it needs to function.
 */
public abstract class BaseWebActivity extends BaseActivity {

    private Content currentContent;
    private HentoidDB db;
    private ObservableWebView webView;
    private boolean webViewIsLoading;
    private FloatingActionButton fabRead, fabDownload, fabRefreshOrStop, fabHome;
    private boolean fabReadEnabled, fabDownloadEnabled;
    private SwipeRefreshLayout swipeLayout;
    private static List<String> blockedAds = new ArrayList<>();

    static
    {
        blockedAds.add("exoclick.com");
        blockedAds.add("juicyadultads.com");
        blockedAds.add("juicyads.com");
        blockedAds.add("exosrv.com");
        blockedAds.add("hentaigold.net");
        blockedAds.add("ads.php");
        blockedAds.add("ads.js");
        blockedAds.add("pop.js");
        blockedAds.add("trafficsan.com");
        blockedAds.add("contentabc.com");
        blockedAds.add("bebi.com");
        blockedAds.add("aftv-serving.bid");
        blockedAds.add("smatoo.net");
    }

    ObservableWebView getWebView() {
        return webView;
    }

    void setWebView(ObservableWebView webView) {
        this.webView = webView;
    }

    abstract Site getStartSite();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_base_web);

        db = HentoidDB.getInstance(this);

        if (getStartSite() == null) {
            Timber.w("Site is null!");
        } else {
            Timber.d("Loading site: %s", getStartSite());
        }

        fabRead = findViewById(R.id.fabRead);
        fabDownload = findViewById(R.id.fabDownload);
        fabRefreshOrStop = findViewById(R.id.fabRefreshStop);
        fabHome = findViewById(R.id.fabHome);

        hideFab(fabRead);
        hideFab(fabDownload);

        initWebView();
        initSwipeLayout();

        setWebView(getWebView());

        String intentVar = getIntent().getStringExtra(Consts.INTENT_URL);
        webView.loadUrl(intentVar == null ? getStartSite().getUrl() : intentVar);
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        webView.removeAllViews();
        webView.destroy();
        webView = null;

        super.onDestroy();
    }

    // Validate permissions
    private void checkPermissions() {
        if (Helper.permissionsCheck(this, ConstsImport.RQST_STORAGE_PERMISSION, false)) {
            Timber.d("Storage permission allowed!");
        } else {
            Timber.d("Storage permission denied!");
            reset();
        }
    }

    private void reset() {
        Helper.reset(HentoidApp.getAppContext(), this);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        webView = findViewById(R.id.wbMain);
        webView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = webView.getHitTestResult();
            if (result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                if (result.getExtra() != null && result.getExtra().contains(getStartSite().getUrl())) {
                    backgroundRequest(result.getExtra());
                }
            } else {
                return true;
            }

            return false;
        });
        webView.setHapticFeedbackEnabled(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100) {
                    swipeLayout.post(() -> swipeLayout.setRefreshing(false));
                } else {
                    swipeLayout.post(() -> swipeLayout.setRefreshing(true));
                }
            }
        });
        webView.setOnScrollChangedCallback((l, t) -> {
            if (!webViewIsLoading) {
                if (webView.canScrollVertically(1) || t == 0) {
                    fabRefreshOrStop.show();
                    fabHome.show();
                    if (fabReadEnabled) {
                        fabRead.show();
                    } else if (fabDownloadEnabled) {
                        fabDownload.show();
                    }
                } else {
                    fabRefreshOrStop.hide();
                    fabHome.hide();
                    fabRead.hide();
                    fabDownload.hide();
                }
            }
        });
        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        String userAgent;
        try {
            userAgent = Helper.getAppUserAgent(this);
        } catch (PackageManager.NameNotFoundException e) {
            userAgent = Consts.USER_AGENT;
        }
        webSettings.setUserAgentString(userAgent);

        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
    }

    private void initSwipeLayout() {
        swipeLayout = findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(() -> {
            if (!swipeLayout.isRefreshing() || !webViewIsLoading) {
                webView.reload();
            }
        });
        swipeLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    public void onRefreshStopFabClick(View view) {
        if (webViewIsLoading) {
            webView.stopLoading();
        } else {
            webView.reload();
        }
    }

    private void goHome() {
        Intent intent = new Intent(this, DownloadsActivity.class);
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (!getWebView().canGoBack()) {
            goHome();
        }
    }

    public void onHomeFabClick(View view) {
        goHome();
    }

    public void onReadFabClick(View view) {
        if (currentContent != null) {
            currentContent = db.selectContentById(currentContent.getId());
            if (currentContent != null) {
                if (StatusContent.DOWNLOADED == currentContent.getStatus()
                        || StatusContent.ERROR == currentContent.getStatus()) {
                    FileHelper.openContent(this, currentContent);
                } else {
                    hideFab(fabRead);
                }
            }
        }
    }

    public void onDownloadFabClick(View view) {
        processDownload();
    }

    void processDownload() {
        currentContent = db.selectContentById(currentContent.getId());
        if (currentContent != null && StatusContent.DOWNLOADED == currentContent.getStatus()) {
            Helper.toast(this, R.string.already_downloaded);
            hideFab(fabDownload);

            return;
        }
        Helper.toast(this, R.string.add_to_queue);

        currentContent.setDownloadDate(new Date().getTime())
                .setStatus(StatusContent.DOWNLOADING);

        db.updateContentStatus(currentContent);
        List<Pair<Integer,Integer>> queue = db.selectQueue();
        int lastIndex = 1;
        if (queue.size() > 0)
        {
            lastIndex = queue.get(queue.size()-1).second + 1;
        }
        db.insertQueue(currentContent, lastIndex);

        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, ContentDownloadService.class);
        startService(intent);

        hideFab(fabDownload);
    }

    private void hideFab(FloatingActionButton fab) {
        fab.hide();
        if (fab.equals(fabDownload)) {
            fabDownloadEnabled = false;
        } else if (fab.equals(fabRead)) {
            fabReadEnabled = false;
        }
    }

    private void showFab(FloatingActionButton fab) {
        fab.show();
        if (fab.equals(fabDownload)) {
            fabDownloadEnabled = true;
        } else if (fab.equals(fabRead)) {
            fabReadEnabled = true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            WebBackForwardList webBFL = webView.copyBackForwardList();
            int i = webBFL.getCurrentIndex();
            do {
                i--;
            }
            while (i >= 0 && webView.getOriginalUrl()
                    .equals(webBFL.getItemAtIndex(i).getOriginalUrl()));
            if (webView.canGoBackOrForward(i - webBFL.getCurrentIndex())) {
                webView.goBackOrForward(i - webBFL.getCurrentIndex());
            } else {
                super.onBackPressed();
            }

            return true;
        }

        return false;
    }

    void processContent(Content content) {
        if (content == null) {
            return;
        }

        addContentToDB(content);

        StatusContent contentStatus = content.getStatus();
        if (contentStatus != StatusContent.DOWNLOADED
                && contentStatus != StatusContent.DOWNLOADING) {
            currentContent = content;
            runOnUiThread(() -> showFab(fabDownload));
        } else {
            runOnUiThread(() -> hideFab(fabDownload));
        }
        if (contentStatus == StatusContent.DOWNLOADED
                || contentStatus == StatusContent.ERROR) {
            currentContent = content;
            runOnUiThread(() -> showFab(fabRead));
        } else {
            runOnUiThread(() -> hideFab(fabRead));
        }

        // Allows debugging parsers without starting a content download
        if (BuildConfig.DEBUG) {
            attachToDebugger(content);
        }
    }

    private void addContentToDB(Content content) {
        Content contentDB = db.selectContentById(content.getUrl().hashCode());
        if (contentDB != null) {
            content.setStatus(contentDB.getStatus())
                    .setImageFiles(contentDB.getImageFiles())
                    .setStorageFolder(contentDB.getStorageFolder())
                    .setDownloadDate(contentDB.getDownloadDate());
        }
        db.insertContent(content);
    }

    private void attachToDebugger(Content content) {
        ContentParser parser = ContentParserFactory.getInstance().getParser(content);
        parser.parseImageList(content);
    }

    void backgroundRequest(String extra) {
        Timber.d("Extras: %s", extra);
    }

    class CustomWebViewClient extends WebViewClient {

        private String domainName = "";
        private final String filteredUrl;
        protected final BaseWebActivity activity;
        protected final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());

        void restrictTo(String s) {
            domainName = s;
        }

        CustomWebViewClient(BaseWebActivity activity, String filteredUrl)
        {
            this.activity = activity;
            this.filteredUrl = filteredUrl;
        }
        CustomWebViewClient(BaseWebActivity activity)
        {
            this.activity = activity;
            this.filteredUrl = "";
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            String hostStr = Uri.parse(url).getHost();
            return hostStr != null && !hostStr.contains(domainName);
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String hostStr = Uri.parse(request.getUrl().toString()).getHost();
            return hostStr != null && !hostStr.contains(domainName);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            webViewIsLoading = true;
            fabRefreshOrStop.setImageResource(R.drawable.ic_action_clear);
            fabRefreshOrStop.show();
            fabHome.show();
            hideFab(fabDownload);
            hideFab(fabRead);

            if (filteredUrl.length() > 0)
            {
                Pattern pattern = Pattern.compile(filteredUrl);
                Matcher matcher = pattern.matcher(url);

                if (matcher.find()) {
                    executeAsyncTask(new HtmlLoader(activity), url);
                }
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            webViewIsLoading = false;
            fabRefreshOrStop.setImageResource(R.drawable.ic_action_refresh);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            if (isUrlForbidden(url)) {
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
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }

    protected boolean isUrlForbidden(String url)
    {
        for(String s : blockedAds)
        {
            if (url.contains(s)) return true;
        }

        return false;
    }

    protected static class HtmlLoader extends AsyncTask<String, Integer, Content> {

        private WeakReference<BaseWebActivity> activityReference;

        // only retain a weak reference to the activity
        HtmlLoader(BaseWebActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected Content doInBackground(String... params) {
            String url = params[0];
            BaseWebActivity activity = activityReference.get();
            try {
                ContentParser parser = ContentParserFactory.getInstance().getParser(activity.getStartSite());
                activity.processContent(parser.parseContent(url));
            } catch (Exception e) {
                Timber.e(e, "Error parsing content.");
                activity.runOnUiThread(() -> Helper.toast(HentoidApp.getAppContext(), R.string.web_unparsable));
            }

            return null;
        }
    }
}
