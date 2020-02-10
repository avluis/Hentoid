package me.devsaki.hentoid.activities.sources;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.json.UpdateInfo;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.views.NestedScrollWebView;
import okhttp3.Response;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
import timber.log.Timber;

import static me.devsaki.hentoid.util.Helper.getChromeVersion;
import static me.devsaki.hentoid.util.HttpHelper.HEADER_CONTENT_TYPE;

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
public abstract class BaseWebActivity extends AppCompatActivity implements WebContentListener {

    protected static final int MODE_DL = 0;
    private static final int MODE_QUEUE = 1;
    private static final int MODE_READ = 2;

    private static final int STATUS_UNKNOWN = 0;
    private static final int STATUS_IN_COLLECTION = 1;
    private static final int STATUS_IN_QUEUE = 2;

    // === UI
    // Associated webview
    protected NestedScrollWebView webView;
    // Toolbar buttons
    private MenuItem backMenu;
    private MenuItem forwardMenu;
    private MenuItem galleryMenu;
    private MenuItem refreshStopMenu;
    private MenuItem actionMenu;
    // Swipe layout
    private SwipeRefreshLayout swipeLayout;
    // Alert message panel and text
    private View alertBanner;
    private ImageView alertIcon;
    private TextView alertMessage;

    // === VARIABLES
    // Currently viewed content
    private Content currentContent;
    // Database
    private ObjectBoxDB db;
    // Indicates which mode the download button is in
    protected int actionButtonMode;
    private CustomWebViewClient webClient;
    // Version iof installed Chrome client
    private int chromeVersion;
    // Alert to be displayed
    private UpdateInfo.SourceAlert alert;

    // List of blocked content (ads or annoying images) -- will be replaced by a blank stream
    private static final List<String> universalBlockedContent = new ArrayList<>();      // Universal list (applied to all sites)
    private List<String> localBlockedContent;                                           // Local list (applied to current site)
    // List of "dirty" elements (CSS selector) to be cleaned before displaying the page
    private List<String> dirtyElements;

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
        universalBlockedContent.add("adtng.net");
        universalBlockedContent.add("adtng.com");
        universalBlockedContent.add("popads.net");
        universalBlockedContent.add("adsco.re");
        universalBlockedContent.add("s24hc8xzag.com");
        universalBlockedContent.add("/nutaku/");
        universalBlockedContent.add("trafficjunky");
        universalBlockedContent.add("traffichaus");
    }

    protected abstract CustomWebViewClient getWebClient();

    abstract Site getStartSite();

    /**
     * Add an content block filter to current site
     *
     * @param filter Filter to addAll to content block system
     */
    protected void addContentBlockFilter(String[] filter) {
        if (null == localBlockedContent) localBlockedContent = new ArrayList<>();
        Collections.addAll(localBlockedContent, filter);
    }

    /**
     * Add an element filter to current site
     *
     * @param elements Elements (CSS selector) to addAll to page cleaner
     */
    protected void addDirtyElements(String[] elements) {
        if (null == dirtyElements) dirtyElements = new ArrayList<>();
        Collections.addAll(dirtyElements, elements);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.applyTheme(this);

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        setContentView(R.layout.activity_base_web);

        db = ObjectBoxDB.getInstance(this);

        if (getStartSite() == null) {
            Timber.w("Site is null!");
        } else {
            Timber.d("Loading site: %s", getStartSite());
        }

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> goHome());
        toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);

        BottomNavigationView bottomToolbar = findViewById(R.id.bottom_navigation);
        bottomToolbar.setOnNavigationItemSelectedListener(this::onMenuItemSelected);
        bottomToolbar.setItemIconTintList(null); // Hack to make selector resource work
        refreshStopMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_refresh_stop);
        backMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_back);
        forwardMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_forward);
        galleryMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_gallery);
        actionMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_download);

        initWebView();
        initSwipeLayout();
        webView.loadUrl(getStartUrl());

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        // Alert banner
        alertBanner = findViewById(R.id.web_alert_group);
        alertIcon = findViewById(R.id.web_alert_icon);
        alertMessage = findViewById(R.id.web_alert_txt);
        displayAlertBanner();
    }

    private String getStartUrl() {
        // Priority 1 : URL specifically given to the activity ("view source" action)
        if (getIntent().getExtras() != null) {
            BaseWebActivityBundle.Parser parser = new BaseWebActivityBundle.Parser(getIntent().getExtras());
            String intentUrl = parser.getUrl();
            if (!intentUrl.isEmpty()) return intentUrl;
        }

        // Priority 2 : Last viewed position, if option activated
        if (Preferences.isBrowserResumeLast()) {
            SiteHistory siteHistory = db.getHistory(getStartSite());
            if (siteHistory != null && !siteHistory.getUrl().isEmpty()) return siteHistory.getUrl();
        }

        // Default site URL
        return getStartSite().getUrl();
    }

    private boolean onMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.web_menu_back:
                this.onBackClick();
                break;
            case R.id.web_menu_forward:
                this.onForwardClick();
                break;
            case R.id.web_menu_gallery:
                this.onGalleryClick();
                break;
            case R.id.web_menu_refresh_stop:
                this.onRefreshStopClick();
                break;
            case R.id.web_menu_copy:
                this.onCopyClick();
                break;
            case R.id.web_menu_download:
                this.onActionFabClick();
                break;
            default:
                return false;
        }
        return true;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateEvent(UpdateEvent event) {
        if (event.sourceAlerts.containsKey(getStartSite())) {
            alert = event.sourceAlerts.get(getStartSite());
            displayAlertBanner();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkPermissions();
    }

    @Override
    protected void onStop() {
        db.insertSiteHistory(getStartSite(), webView.getUrl());
        super.onStop();
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

        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    // Validate permissions
    private void checkPermissions() {
        if (PermissionUtil.checkExternalStoragePermission(this)) {
            Timber.d("Storage permission allowed!");
        } else {
            Timber.d("Storage permission denied!");
            HentoidApp.reset(this);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {

        try {
            webView = new NestedScrollWebView(this);
        } catch (Resources.NotFoundException e) {
            // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
            // Creating with the application Context fixes this, but is not generally recommended for view creation
            webView = new NestedScrollWebView(Helper.getFixedContext(this));
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }


        webClient = getWebClient();
        webView.setWebViewClient(webClient);

        // Download immediately on long click on a link / image link
        if (Preferences.isBrowserQuickDl())
            webView.setOnLongClickListener(v -> {
                WebView.HitTestResult result = webView.getHitTestResult();

                String url = "";
                // Plain link
                if (result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE && result.getExtra() != null)
                    url = result.getExtra();

                // Image link (https://stackoverflow.com/a/55299801/8374722)
                if (result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    Handler handler = new Handler();
                    Message message = handler.obtainMessage();

                    webView.requestFocusNodeHref(message);
                    url = message.getData().getString("url");
                }

                if (url != null && !url.isEmpty() && webClient.isPageFiltered(url)) {
                    // Launch on a new thread to avoid crashes
                    webClient.parseResponseAsync(url);
                    return true;
                } else {
                    return false;
                }
            });


        Timber.i("Using agent %s", webView.getSettings().getUserAgentString());
        chromeVersion = getChromeVersion(this);

        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        webSettings.setUserAgentString(Consts.USER_AGENT_NEUTRAL);

        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);

        CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        SwipeRefreshLayout refreshLayout = findViewById(R.id.swipe_container);
        if (refreshLayout != null) refreshLayout.addView(webView, layoutParams);
    }

    private void displayAlertBanner() {
        if (alertMessage != null && alert != null) {
            alertIcon.setImageResource(alert.getStatus().getIcon());
            alertMessage.setText(formatAlertMessage(alert));
            alertBanner.setVisibility(View.VISIBLE);
        }
    }

    private void initSwipeLayout() {
        swipeLayout = findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(() -> {
            if (!swipeLayout.isRefreshing() || !webClient.isLoading()) {
                webView.reload();
            }
        });
        swipeLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    private void refreshNavigationMenu() {
        backMenu.setEnabled(webView.canGoBack());
        forwardMenu.setEnabled(webView.canGoForward());
        galleryMenu.setEnabled(backListContainsGallery(webView.copyBackForwardList()) > -1);
    }

    private void onBackClick() {
        webView.goBack();
    }

    private void onForwardClick() {
        webView.goForward();
    }

    private void onGalleryClick() {
        WebBackForwardList list = webView.copyBackForwardList();
        int galleryIndex = backListContainsGallery(list);
        if (galleryIndex > -1) webView.goBackOrForward(galleryIndex - list.getCurrentIndex());
    }

    private void onRefreshStopClick() {
        if (webClient.isLoading()) webView.stopLoading();
        else webView.reload();
    }

    private void onCopyClick() {
        if (Helper.copyPlainTextToClipboard(this, webView.getUrl()))
            ToastUtil.toast(R.string.web_url_clipboard);
    }

    private void goHome() {
        Intent intent = new Intent(this, LibraryActivity.class);
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

    public void onAlertCloseClick(View view) {
        alertBanner.setVisibility(View.GONE);
    }

    /**
     * Listener for Action floating action button : download content, view queue or read content
     */
    public void onActionFabClick() {
        if (MODE_DL == actionButtonMode) processDownload(false);
        else if (MODE_QUEUE == actionButtonMode) goToQueue();
        else if (MODE_READ == actionButtonMode && currentContent != null) {
            currentContent = db.selectContentBySourceAndUrl(currentContent.getSite(), currentContent.getUrl());
            if (currentContent != null && (StatusContent.DOWNLOADED == currentContent.getStatus()
                    || StatusContent.ERROR == currentContent.getStatus()
                    || StatusContent.MIGRATED == currentContent.getStatus()))
                ContentHelper.openHentoidViewer(this, currentContent, null);
            else actionMenu.setEnabled(false);
        }
    }

    private void changeFabActionMode(int mode) {
        @DrawableRes int resId = R.drawable.ic_info;
        if (MODE_DL == mode) {
            resId = R.drawable.selector_download_action;
        } else if (MODE_QUEUE == mode) {
            resId = R.drawable.ic_action_queue;
        } else if (MODE_READ == mode) {
            resId = R.drawable.ic_action_play;
        }
        actionButtonMode = mode;
        actionMenu.setIcon(resId);
        actionMenu.setEnabled(true);
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     */
    void processDownload(boolean quickDownload) {
        if (null == currentContent) return;

        if (currentContent.getId() > 0)
            currentContent = db.selectContentById(currentContent.getId());

        if (null == currentContent) return;

        if (StatusContent.DOWNLOADED == currentContent.getStatus()) {
            ToastUtil.toast(R.string.already_downloaded);
            if (!quickDownload) changeFabActionMode(MODE_READ);
            return;
        }
        ToastUtil.toast(R.string.add_to_queue);

        currentContent.setStatus(StatusContent.DOWNLOADING);
        db.insertContent(currentContent);

        List<QueueRecord> queue = db.selectQueue();
        int lastIndex = 1;
        if (!queue.isEmpty()) {
            lastIndex = queue.get(queue.size() - 1).rank + 1;
        }
        db.insertQueue(currentContent.getId(), lastIndex);

        if (Preferences.isQueueAutostart()) ContentQueueManager.getInstance().resumeQueue(this);

        if (!quickDownload) changeFabActionMode(MODE_QUEUE);
    }

    private void goToQueue() {
        Intent intent = new Intent(this, QueueActivity.class);
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
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
    private int processContent(@NonNull Content content, boolean quickDownload) {
        int result = STATUS_UNKNOWN;
        if (null == content.getUrl()) return result;

        Timber.i("Content Site, URL : %s, %s", content.getSite().getCode(), content.getUrl());
        Content contentDB = db.selectContentBySourceAndUrl(content.getSite(), content.getUrl());

        boolean isInCollection = (contentDB != null && (
                contentDB.getStatus().equals(StatusContent.DOWNLOADED)
                        || contentDB.getStatus().equals(StatusContent.MIGRATED)
                        || contentDB.getStatus().equals(StatusContent.ERROR)
        ));
        boolean isInQueue = (contentDB != null && (
                contentDB.getStatus().equals(StatusContent.DOWNLOADING)
                        || contentDB.getStatus().equals(StatusContent.PAUSED)
        ));

        if (!isInCollection && !isInQueue) {
            if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB
                content.setStatus(StatusContent.SAVED);
                content.populateAuthor();
                db.insertContent(content);
            } else {
                content = contentDB;
            }
            if (!quickDownload) changeFabActionMode(MODE_DL);
        }

        if (isInCollection) {
            if (!quickDownload) changeFabActionMode(MODE_READ);
            result = STATUS_IN_COLLECTION;
        }
        if (isInQueue) {
            if (!quickDownload) changeFabActionMode(MODE_QUEUE);
            result = STATUS_IN_QUEUE;
        }

        currentContent = content;
        return result;
    }

    public void onResultReady(@NonNull Content results, boolean quickDownload) {
        int status = processContent(results, quickDownload);
        if (quickDownload) {
            if (STATUS_UNKNOWN == status) processDownload(quickDownload);
            else if (STATUS_IN_COLLECTION == status) ToastUtil.toast(R.string.already_downloaded);
            else if (STATUS_IN_QUEUE == status) ToastUtil.toast(R.string.already_queued);
        }
    }

    public void onResultFailed() {
        runOnUiThread(() -> ToastUtil.toast(R.string.web_unparsable));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE && event.content != null && event.content.getId() == currentContent.getId()) {
            changeFabActionMode(MODE_READ);
        }
    }

    /**
     * Analyze loaded HTML to display download button
     * Override blocked content with empty content
     */
    class CustomWebViewClient extends WebViewClient {

        final CompositeDisposable compositeDisposable = new CompositeDisposable();
        private final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());
        protected final WebContentListener listener;
        private final List<Pattern> filteredUrlPattern = new ArrayList<>();
        private final HtmlAdapter<ContentParser> htmlAdapter;

        private String restrictedDomainName = "";
        private boolean isPageLoading = false;
        boolean isHtmlLoaded = false;


        @SuppressWarnings("unchecked")
        CustomWebViewClient(String[] filteredUrl, WebContentListener listener) {
            this.listener = listener;

            Class c = ContentParserFactory.getInstance().getContentParserClass(getStartSite());
            final Jspoon jspoon = Jspoon.create();
            htmlAdapter = jspoon.adapter(c); // Unchecked but alright

            for (String s : filteredUrl) filteredUrlPattern.add(Pattern.compile(s));
        }

        void destroy() {
            Timber.d("WebClient destroyed");
            compositeDisposable.clear();
        }

        void restrictTo(String s) {
            restrictedDomainName = s;
        }

        boolean isPageFiltered(String url) {
            if (filteredUrlPattern.isEmpty()) return false;

            for (Pattern p : filteredUrlPattern) {
                Matcher matcher = p.matcher(url);
                if (matcher.find()) return true;
            }
            return false;
        }

        /**
         * Indicates if the given URL is forbidden by the current content filters
         *
         * @param url URL to be examinated
         * @return True if URL is forbidden according to current filters; false if not
         */
        private boolean isUrlForbidden(String url) {
            for (String s : universalBlockedContent) {
                if (url.contains(s)) return true;
            }
            if (localBlockedContent != null)
                for (String s : localBlockedContent) {
                    if (url.contains(s)) return true;
                }
            return false;
        }

        /**
         * Determines if the browser can use one single OkHttp request to serve HTML pages
         * - Does not work on 4.4 & 4.4.2 because calling CookieManager.getCookie inside shouldInterceptRequest triggers a deadlock
         * https://issuetracker.google.com/issues/36989494
         * - Does not work on Chrome 45-71 because sameSite cookies are not published by CookieManager.getCookie (causes session issues on nHentai)
         * https://bugs.chromium.org/p/chromium/issues/detail?id=780491
         *
         * @return true if HTML content can be served by a single OkHttp request,
         * false if the webview has to handle the display (OkHttp will be used as a 2nd request for parsing)
         */
        private boolean canUseSingleOkHttpRequest() {
            return (Preferences.isBrowserAugmented()
                    && Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH
                    && (chromeVersion < 45 || chromeVersion > 71)
            );
        }

        @Override
        @Deprecated
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            String hostStr = Uri.parse(url).getHost();
            return hostStr != null && !hostStr.contains(restrictedDomainName);
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String hostStr = Uri.parse(request.getUrl().toString()).getHost();
            return hostStr != null && !hostStr.contains(restrictedDomainName);
        }

        /**
         * Important note
         * <p>
         * Based on observation, for a given URL, onPageStarted seems to be called
         * - Before {@link this.shouldInterceptRequest} when the page is not cached (1st call)
         * - After {@link this.shouldInterceptRequest} when the page is cached (Nth call; N>1)
         */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            refreshStopMenu.setIcon(R.drawable.ic_close);
            isPageLoading = true;
            if (!isHtmlLoaded) {
                actionMenu.setIcon(R.drawable.selector_download_action);
                actionMenu.setEnabled(false);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            isPageLoading = false;
            isHtmlLoaded = false; // Reset for the next page
            refreshStopMenu.setIcon(R.drawable.ic_action_refresh);
            refreshNavigationMenu();
        }

        @Override
        @Deprecated
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            // Prevents processing the page twice on Lollipop and above
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                WebResourceResponse result = shouldInterceptRequestInternal(url, null);
                if (result != null) return result;
            }
            return super.shouldInterceptRequest(view, url);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            // Data fetched with POST is out of scope of analysis and adblock
            if (!request.getMethod().equalsIgnoreCase("get")) {
                Timber.d("[%s] ignoring; method = %s", request.getUrl().toString(), request.getMethod());
                return super.shouldInterceptRequest(view, request);
            }

            String url = request.getUrl().toString();
            WebResourceResponse result = shouldInterceptRequestInternal(url, request.getRequestHeaders());
            if (result != null) return result;
            else return super.shouldInterceptRequest(view, request);
        }

        @Nullable
        private WebResourceResponse shouldInterceptRequestInternal(@NonNull String url,
                                                                   @Nullable Map<String, String> headers) {
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
                if (isPageFiltered(url)) return parseResponse(url, headers, true, false);
                // If we're here to remove "dirty elements", we only do it on HTML resources (URLs without extension)
                if (dirtyElements != null && HttpHelper.getExtensionFromUri(url).isEmpty())
                    return parseResponse(url, headers, false, false);

                return null;
            }
        }

        void parseResponseAsync(@NonNull String urlStr) {
            compositeDisposable.add(
                    Completable.fromCallable(() -> parseResponse(urlStr, null, true, true))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> {
                            }, Timber::e)
            );
        }

        @SuppressLint("NewApi")
        @WorkerThread
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            // If we're here for dirty content removal only, and can't use the OKHTTP request, it's no use going further
            if (!analyzeForDownload && !canUseSingleOkHttpRequest()) return null;

            List<Pair<String, String>> requestHeadersList = new ArrayList<>();

            if (requestHeaders != null)
                for (String key : requestHeaders.keySet())
                    requestHeadersList.add(new Pair<>(key, requestHeaders.get(key)));

            if (canUseSingleOkHttpRequest()) {
                String cookie = CookieManager.getInstance().getCookie(urlStr);
                if (cookie != null)
                    requestHeadersList.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookie));
            }

            try {
                // Query resource here, using OkHttp
                Response response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, getStartSite().canKnowHentoidAgent());
                if (null == response.body()) throw new IOException("Empty body");

                InputStream parserStream;
                WebResourceResponse result;
                if (canUseSingleOkHttpRequest()) {
                    InputStream browserStream;
                    if (analyzeForDownload) {
                        // Response body bytestream needs to be duplicated
                        // because Jsoup closes it, which makes it unavailable for the WebView to use
                        List<InputStream> is = Helper.duplicateInputStream(response.body().byteStream(), 2);
                        parserStream = is.get(0);
                        browserStream = is.get(1);
                    } else {
                        parserStream = null;
                        browserStream = response.body().byteStream();
                    }

                    // Remove dirty elements from HTML resources
                    if (dirtyElements != null) {
                        String mimeType = response.header(HEADER_CONTENT_TYPE);
                        if (mimeType != null) {
                            mimeType = HttpHelper.cleanContentType(mimeType).first.toLowerCase();
                            if (mimeType.contains("html"))
                                browserStream = removeCssElementsFromStream(browserStream, urlStr, dirtyElements);
                        }
                    }

                    // Convert OkHttp response to the expected format
                    result = HttpHelper.okHttpResponseToWebResourceResponse(response, browserStream);

                    // Manually set cookie if present in response header (won't be set by Android if we don't do this)
                    if (result.getResponseHeaders().containsKey("set-cookie")) {
                        String cookiesStr = result.getResponseHeaders().get("set-cookie");
                        if (cookiesStr != null) {
                            Map<String, String> cookies = HttpHelper.parseCookies(cookiesStr);
                            HttpHelper.setDomainCookies(urlStr, cookies);
                        }
                    }
                } else {
                    parserStream = response.body().byteStream();
                    result = null; // Default webview behaviour
                }

                if (analyzeForDownload)
                    compositeDisposable.add(
                            Single.fromCallable(() -> htmlAdapter.fromInputStream(parserStream, new URL(urlStr)).toContent(urlStr))
                                    .subscribeOn(Schedulers.computation())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            content -> processContent(content, requestHeadersList, quickDownload),
                                            throwable -> {
                                                Timber.e(throwable, "Error parsing content.");
                                                isHtmlLoaded = true;
                                                listener.onResultFailed();
                                            })
                    );

                return result;
            } catch (MalformedURLException e) {
                Timber.e(e, "Malformed URL : %s", urlStr);
            } catch (IOException e) {
                Timber.e(e);
            }
            return null;
        }

        private void processContent(@Nonnull Content content, @Nonnull List<Pair<String, String>> headersList, boolean quickDownload) {
            if (content.getStatus() != null && content.getStatus().equals(StatusContent.IGNORED))
                return;

            // Save cookies for future calls during download
            Map<String, String> params = new HashMap<>();
            for (Pair<String, String> p : headersList)
                if (p.first.equals(HttpHelper.HEADER_COOKIE_KEY))
                    params.put(HttpHelper.HEADER_COOKIE_KEY, p.second);

            content.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
            isHtmlLoaded = true;

            listener.onResultReady(content, quickDownload);
        }

        /**
         * Indicated whether the current webpage is still loading or not
         *
         * @return True if current webpage is being loaded; false if not
         */
        boolean isLoading() {
            return isPageLoading;
        }

        private InputStream removeCssElementsFromStream(@NonNull InputStream stream, @NonNull String baseUri, @NonNull List<String> dirtyElements) {
            try {
                Document doc = Jsoup.parse(stream, null, baseUri);
                for (String s : dirtyElements)
                    for (Element e : doc.select(s)) {
                        Timber.d("[%s] Removing node %s", baseUri, e.toString());
                        e.remove();
                    }
                return new ByteArrayInputStream(doc.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Timber.e(e);
                return stream;
            }
        }

    }

    private int backListContainsGallery(WebBackForwardList backForwardList) {
        for (int i = backForwardList.getCurrentIndex() - 1; i >= 0; i--) {
            WebHistoryItem item = backForwardList.getItemAtIndex(i);
            if (webClient.isPageFiltered(item.getUrl())) return i;
        }
        return -1;
    }

    private String formatAlertMessage(@NonNull UpdateInfo.SourceAlert alert) {
        String result = "";

        // Main message body
        if (alert.getStatus().equals(AlertStatus.ORANGE)) {
            result = getResources().getString(R.string.alert_orange);
        } else if (alert.getStatus().equals(AlertStatus.RED)) {
            result = getResources().getString(R.string.alert_red);
        } else if (alert.getStatus().equals(AlertStatus.GREY)) {
            result = getResources().getString(R.string.alert_grey);
        } else if (alert.getStatus().equals(AlertStatus.BLACK)) {
            result = getResources().getString(R.string.alert_black);
        }

        // End of message
        if (alert.getFixedByBuild() < Integer.MAX_VALUE)
            result = result.replace("%s", getResources().getString(R.string.alert_fix_available));
        else result = result.replace("%s", getResources().getString(R.string.alert_wip));

        return result;
    }
}
