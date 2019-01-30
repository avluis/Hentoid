package me.devsaki.hentoid.activities.websites;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.views.ObservableWebView;
import timber.log.Timber;

/**
 * Browser activity which allows the user to navigate a supported source.
 * No particular source should be filtered/defined here.
 * The source itself should contain every method it needs to function.
 * <p>
 * todo issue:
 * {@link #checkPermissions()} causes the app to reset unexpectedly. If permission is integral to
 * this activity's function, it is recommended to request for this permission and show rationale if
 * permission request is denied
 */
public abstract class BaseWebActivity extends BaseActivity implements ResultListener<Content> {

    // UI
    protected ObservableWebView webView;                                              // Associated webview
    private FloatingActionButton fabRead, fabDownload, fabRefreshOrStop, fabHome;   // Action buttons
    private SwipeRefreshLayout swipeLayout;

    // Content currently viewed
    private Content currentContent;
    // Database
    private HentoidDB db;
    // Indicates if webView is loading
    private boolean webViewIsLoading;
    // Indicates if corresponding action buttons are enabled
    private boolean fabReadEnabled, fabDownloadEnabled;

    protected CustomWebViewClient webClient;

    // List of blocked content (ads or annoying images) -- will be replaced by a blank stream
    private static final List<String> universalBlockedContent = new ArrayList<>();      // Universal list (applied to all sites)
    private List<String> localBlockedContent;                                           // Local list (applied to current site)

    static {
        universalBlockedContent.add("exoclick.com");
        universalBlockedContent.add("juicyadultads.com");
        universalBlockedContent.add("juicyads.com");
        universalBlockedContent.add("exosrv.com");
        universalBlockedContent.add("hentaigold.net");
        universalBlockedContent.add("ads.php");
        universalBlockedContent.add("ads.js");
        universalBlockedContent.add("pop.js");
        universalBlockedContent.add("trafficsan.com");
        universalBlockedContent.add("contentabc.com");
        universalBlockedContent.add("bebi.com");
        universalBlockedContent.add("aftv-serving.bid");
        universalBlockedContent.add("smatoo.net");
    }

    protected abstract CustomWebViewClient getWebClient();

    abstract Site getStartSite();


    /**
     * Add an content block filter to current site
     *
     * @param filter Filter to add to content block system
     */
    protected void addContentBlockFilter(String[] filter) {
        if (null == localBlockedContent) localBlockedContent = new ArrayList<>();
        Collections.addAll(localBlockedContent, filter);
    }

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
        if (webClient != null) webClient.destroy();
        webClient = null;

        if (webView != null) {
            // the WebView must be removed from the view hierarchy before calling destroy
            // to prevent a memory leak
            // See https://developer.android.com/reference/android/webkit/WebView.html#destroy%28%29
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    // Validate permissions
    private void checkPermissions() {
        if (PermissionUtil.checkExternalStoragePermission(this)) {
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

        boolean bWebViewOverview = Preferences.getWebViewOverview();
        int webViewInitialZoom = Preferences.getWebViewInitialZoom();

        if (bWebViewOverview) {
            webView.getSettings().setLoadWithOverviewMode(false);
            webView.setInitialScale(webViewInitialZoom);
            Timber.d("WebView Initial Scale: %s%%", webViewInitialZoom);
        } else {
            webView.setInitialScale(Preferences.Default.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT);
            webView.getSettings().setLoadWithOverviewMode(true);
        }

        webClient = getWebClient();
        webView.setWebViewClient(webClient);

        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        webSettings.setUserAgentString(Consts.USER_AGENT);

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
        if (!webView.canGoBack()) {
            goHome();
        }
    }

    /**
     * Listener for Home floating action button : go back to Library view
     *
     * @param view Calling view (part of the mandatory signature)
     */
    public void onHomeFabClick(View view) {
        goHome();
    }

    /**
     * Listener for Read floating action button : open content when it is already part of the library
     *
     * @param view Calling view (part of the mandatory signature)
     */
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

    /**
     * Listener for Download floating action button : start content download
     *
     * @param view Calling view (part of the mandatory signature)
     */
    public void onDownloadFabClick(View view) {
        processDownload();
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     */
    void processDownload() {
        currentContent = db.selectContentById(currentContent.getId());
        if (currentContent != null && StatusContent.DOWNLOADED == currentContent.getStatus()) {
            ToastUtil.toast(this, R.string.already_downloaded);
            hideFab(fabDownload);

            return;
        }
        ToastUtil.toast(this, R.string.add_to_queue);

        currentContent.setDownloadDate(new Date().getTime())
                .setStatus(StatusContent.DOWNLOADING);
        db.updateContentStatus(currentContent);

        List<Pair<Integer, Integer>> queue = db.selectQueue();
        int lastIndex = 1;
        if (queue.size() > 0) {
            lastIndex = queue.get(queue.size() - 1).second + 1;
        }
        db.insertQueue(currentContent.getId(), lastIndex);

        ContentQueueManager.getInstance().resumeQueue(this);

        hideFab(fabDownload);
    }

    /**
     * Hide designated Floating Action Button
     *
     * @param fab Reference to the floating action button to hide
     */
    private void hideFab(FloatingActionButton fab) {
        fab.hide();
        if (fab.equals(fabDownload)) {
            fabDownloadEnabled = false;
        } else if (fab.equals(fabRead)) {
            fabReadEnabled = false;
        }
    }

    /**
     * Show designated Floating Action Button
     *
     * @param fab Reference to the floating action button to show
     */
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

    /**
     * Display webview controls according to designated content
     *
     * @param content Currently displayed content
     */
    void processContent(Content content) {
        if (null == content || null == content.getUrl()) {
            return;
        }

        addContentToDB(content);

        // Set Download action button visibility
        StatusContent contentStatus = content.getStatus();
        if (contentStatus != StatusContent.DOWNLOADED
                && contentStatus != StatusContent.DOWNLOADING
                && contentStatus != StatusContent.MIGRATED) {
            currentContent = content;
            runOnUiThread(() -> showFab(fabDownload));
        } else {
            runOnUiThread(() -> hideFab(fabDownload));
        }

        // Set Read action button visibility
        if (contentStatus == StatusContent.DOWNLOADED
                || contentStatus == StatusContent.MIGRATED
                || contentStatus == StatusContent.ERROR) {
            currentContent = content;
            runOnUiThread(() -> showFab(fabRead));
        } else {
            runOnUiThread(() -> hideFab(fabRead));
        }
    }

    /**
     * Add designated Content to the Hentoid DB
     *
     * @param content Content to be added to the DB
     */
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

    public void onResultReady(Content results, int totalContent) {
        processContent(results);
    }

    public void onResultFailed(String message) {
        runOnUiThread(() -> ToastUtil.toast(HentoidApp.getAppContext(), R.string.web_unparsable));
    }


    abstract class CustomWebViewClient extends WebViewClient {

        private String domainName = "";
        private final String filteredUrl;
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        protected final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());
        final Site startSite;
        protected final ResultListener<Content> listener;

        protected abstract void onGalleryFound(String url);


        CustomWebViewClient(String filteredUrl, Site startSite, ResultListener<Content> listener) {
            this.filteredUrl = filteredUrl;
            this.startSite = startSite;
            this.listener = listener;
        }

        void destroy() {
            Timber.d("WebClient destroyed");
            compositeDisposable.clear();
        }

        void restrictTo(String s) {
            domainName = s;
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

            if (filteredUrl.length() > 0) {
                Pattern pattern = Pattern.compile(filteredUrl);
                Matcher matcher = pattern.matcher(url);
                if (matcher.find()) onGalleryFound(url);
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

    /**
     * Indicates if the given URL is forbidden by the current content filters
     *
     * @param url URL to be examinated
     * @return True if URL is forbidden according to current filters; false if not
     */
    protected boolean isUrlForbidden(String url) {
        for (String s : universalBlockedContent) {
            if (url.contains(s)) return true;
        }
        if (localBlockedContent != null)
            for (String s : localBlockedContent) {
                if (url.contains(s)) return true;
            }

        return false;
    }
}
