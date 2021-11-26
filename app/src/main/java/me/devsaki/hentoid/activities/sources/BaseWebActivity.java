package me.devsaki.hentoid.activities.sources;

import static me.devsaki.hentoid.util.PermissionHelper.RQST_STORAGE_PERMISSION;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.BiConsumer;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.skydoves.balloon.ArrowOrientation;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.threeten.bp.Instant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.BaseActivity;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.PrefsActivityBundle;
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.fragments.web.BookmarksDialogFragment;
import me.devsaki.hentoid.fragments.web.DuplicateDialogFragment;
import me.devsaki.hentoid.json.UpdateInfo;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.DuplicateHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.PermissionHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.TooltipHelper;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.views.NestedScrollWebView;
import me.devsaki.hentoid.widget.AddQueueMenu;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Browser activity which allows the user to navigate a supported source.
 * No particular source should be filtered/defined here.
 * The source itself should contain every method it needs to function.
 */
public abstract class BaseWebActivity extends BaseActivity implements CustomWebViewClient.CustomWebActivity, BookmarksDialogFragment.Parent, DuplicateDialogFragment.Parent {

    @IntDef({ActionMode.DOWNLOAD, ActionMode.DOWNLOAD_PLUS, ActionMode.VIEW_QUEUE, ActionMode.READ})
    @Retention(RetentionPolicy.SOURCE)
    protected @interface ActionMode {
        // Download book
        int DOWNLOAD = 0;
        // Download new pages
        int DOWNLOAD_PLUS = 1;
        // Go to the queue screen
        int VIEW_QUEUE = 2;
        // Read downloaded book (image viewer)
        int READ = 3;
    }

    @IntDef({ContentStatus.UNKNOWN, ContentStatus.IN_COLLECTION, ContentStatus.IN_QUEUE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ContentStatus {
        // Content is unknown (i.e. ready to be downloaded)
        int UNKNOWN = 0;
        // Content is already in the library
        int IN_COLLECTION = 1;
        // Content is already queued
        int IN_QUEUE = 2;
    }

    @IntDef({SeekMode.PAGE, SeekMode.GALLERY})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SeekMode {
        // Seek a specific results page
        int PAGE = 0;
        // Back to latest gallery page
        int GALLERY = 1;
    }

    private static final float SIMILARITY_MIN_THRESHOLD = 0.85f;


    // === NUTS AND BOLTS
    private CustomWebViewClient webClient;
    // Database
    private CollectionDAO objectBoxDAO;
    // Disposable to be used for punctual search
    private Disposable searchExtraImagesdisposable;
    // Disposable to be used for content processing
    private Disposable processContentDisposable;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;

    // === UI
    // Associated webview
    protected NestedScrollWebView webView;
    // Top toolbar buttons
    private MenuItem refreshStopMenu;
    private MenuItem bookmarkMenu;
    // Bottom toolbar
    private BottomNavigationView bottomToolbar;
    // Bottom toolbar buttons
    private MenuItem backMenu;
    private MenuItem forwardMenu;
    private MenuItem seekMenu;
    private MenuItem actionMenu;
    private @DrawableRes
    int downloadIcon;
    // Swipe layout
    private SwipeRefreshLayout swipeLayout;
    // Animated check (visual confirmation for quick download)
    ImageView animatedCheck;
    // Alert message panels and text
    private View topAlertBanner;
    private ImageView topAlertIcon;
    private TextView topAlertMessage;
    private View bottomAlertBanner;
    private TextView bottomAlertMessage;
    // Progress bar
    private ProgressBar progressBar;

    // === CURRENTLY VIEWED CONTENT-RELATED VARIABLES
    private Content currentContent = null;
    // Content ID of the duplicate candidate of the currently viewed Content
    private long duplicateId = -1;
    // Similarity score of the duplicate candidate of the currently viewed Content
    private float duplicateSimilarity = 0f;
    // Blocked tags found on the currently viewed Content
    private List<String> blockedTags = Collections.emptyList();
    // Extra images found on the currently viewed Content
    private List<ImageFile> extraImages = Collections.emptyList();
    // TODO doc
    private final List<String> siteGalleries = new ArrayList<>();

    // === OTHER VARIABLES
    // Indicates which mode the download button is in
    protected @ActionMode
    int actionButtonMode;
    // Indicates which mode the seek button is in
    protected @SeekMode
    int seekButtonMode;
    // Alert to be displayed
    private UpdateInfo.SourceAlert alert;
    // Handler for fetch interceptor
    protected BiConsumer<String, String> fetchHandler = null;
    protected String jsInterceptorScript = null;


    protected abstract CustomWebViewClient getWebClient();

    abstract Site getStartSite();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        objectBoxDAO = new ObjectBoxDAO(this);
        Preferences.registerPrefsChangedListener(listener);

        // TODO condition
        updateSiteGalleries();

        setContentView(R.layout.activity_base_web);

        if (getStartSite() == null) {
            Timber.w("Site is null!");
        } else {
            Timber.d("Loading site: %s", getStartSite());
        }

        // Toolbar
        // Top toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
        toolbar.setTitle(getStartSite().getDescription());
        refreshStopMenu = toolbar.getMenu().findItem(R.id.web_menu_refresh_stop);
        bookmarkMenu = toolbar.getMenu().findItem(R.id.web_menu_bookmark);

        bottomToolbar = findViewById(R.id.bottom_navigation);
        bottomToolbar.setOnNavigationItemSelectedListener(this::onMenuItemSelected);
        bottomToolbar.setItemIconTintList(null); // Hack to make selector resource work
        backMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_back);
        forwardMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_forward);
        seekMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_seek);
        actionMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_action);

        // Webview
        animatedCheck = findViewById(R.id.animated_check);
        initUI();
        initSwipeLayout();
        webView.loadUrl(getStartUrl());

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        // Alert banners
        topAlertBanner = findViewById(R.id.top_alert);
        topAlertIcon = findViewById(R.id.top_alert_icon);
        topAlertMessage = findViewById(R.id.top_alert_txt);
        // Banner close buttons
        View topAlertCloseButton = findViewById(R.id.top_alert_close_btn);
        topAlertCloseButton.setOnClickListener(this::onTopAlertCloseClick);
        View bottomAlertCloseButton = findViewById(R.id.bottom_alert_close_btn);
        bottomAlertCloseButton.setOnClickListener(this::onBottomAlertCloseClick);

        bottomAlertBanner = findViewById(R.id.bottom_alert);
        bottomAlertMessage = findViewById(R.id.bottom_alert_txt);

        progressBar = findViewById(R.id.progress_bar);

        downloadIcon = (Preferences.getBrowserDlAction() == Content.DownloadMode.DOWNLOAD) ? R.drawable.selector_download_action : R.drawable.selector_download_stream_action;

        displayTopAlertBanner();
    }

    /**
     * Determine the URL the browser will load at startup
     * - Either an URL specifically given to the activity (e.g. "view source" action)
     * - Or the last viewed page, if the option is enabled
     * - If neither of the previous cases, the default URL of the site
     *
     * @return URL to load at startup
     */
    private String getStartUrl() {
        // Priority 1 : URL specifically given to the activity (e.g. "view source" action)
        if (getIntent().getExtras() != null) {
            BaseWebActivityBundle.Parser parser = new BaseWebActivityBundle.Parser(getIntent().getExtras());
            String intentUrl = parser.getUrl();
            if (!intentUrl.isEmpty()) return intentUrl;
        }

        // Priority 2 : Last viewed position, if option enabled
        if (Preferences.isBrowserResumeLast()) {
            SiteHistory siteHistory = objectBoxDAO.selectHistory(getStartSite());
            if (siteHistory != null && !siteHistory.getUrl().isEmpty()) return siteHistory.getUrl();
        }

        // Default site URL
        return getStartSite().getUrl();
    }

    @SuppressLint("NonConstantResourceId")
    private boolean onMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.web_menu_home:
                this.goHome();
                break;
            case R.id.web_menu_back:
                this.onBackClick();
                break;
            case R.id.web_menu_forward:
                this.onForwardClick();
                break;
            case R.id.web_menu_seek:
                this.onSeekClick();
                break;
            case R.id.web_menu_bookmark:
                this.onBookmarkClick();
                break;
            case R.id.web_menu_refresh_stop:
                this.onRefreshStopClick();
                break;
            case R.id.web_menu_copy:
                this.onCopyClick();
                break;
            case R.id.web_menu_action:
                this.onActionClick();
                break;
            case R.id.web_menu_settings:
                this.onSettingsClick();
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
            displayTopAlertBanner();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onDownloadPreparationEvent(DownloadPreparationEvent event) {
        // Show progress if it's about current content or its best duplicate
        if (
                (currentContent != null && ContentHelper.isInLibrary(currentContent.getStatus()) && event.getRelevantId() == currentContent.getId())
                        || (duplicateId > 0 && event.getRelevantId() == duplicateId)
        ) {
            progressBar.setMax(event.total);
            progressBar.setProgress(event.done);
            progressBar.setProgressTintList(ColorStateList.valueOf(getResources().getColor(R.color.secondary_light)));
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(webView.getUrl());
        outState.putAll(builder.getBundle());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        String url = new BaseWebActivityBundle.Parser(savedInstanceState).getUrl();
        if (url != null && !url.isEmpty())
            webView.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkPermissions();
        String url = webView.getUrl();
        Timber.i(">> WebActivity resume : %s %s %s", url, currentContent != null, (currentContent != null) ? currentContent.getTitle() : "");
        if (currentContent != null && url != null && getWebClient().isGalleryPage(url)) {
            if (processContentDisposable != null)
                processContentDisposable.dispose(); // Cancel whichever process was happening before
            processContentDisposable = Single.fromCallable(() -> processContent(currentContent, false))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            status -> onContentProcessed(status, false),
                            t -> {
                                Timber.e(t);
                                onContentProcessed(ContentStatus.UNKNOWN, false);
                            }
                    );
        }
    }

    @Override
    protected void onStop() {
        if (webView.getUrl() != null)
            objectBoxDAO.insertSiteHistory(getStartSite(), webView.getUrl());
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

        Preferences.unregisterPrefsChangedListener(listener);

        // Cancel any previous extra page load
        EventBus.getDefault().post(new DownloadEvent(currentContent, DownloadEvent.Type.EV_INTERRUPT_CONTENT));

        if (objectBoxDAO != null) objectBoxDAO.cleanup();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    // Validate permissions
    // TODO find something better than that
    private void checkPermissions() {
        if (!PermissionHelper.requestExternalStorageReadWritePermission(this, RQST_STORAGE_PERMISSION))
            ToastHelper.toast("Storage permission denied - cannot use the downloader");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initUI() {

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
            webView.setInitialScale(Preferences.Default.WEBVIEW_INITIAL_ZOOM_DEFAULT);
            webView.getSettings().setLoadWithOverviewMode(true);
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);


        webClient = getWebClient();
        webView.setWebViewClient(webClient);

        // Download immediately on long click on a link / image link
        if (Preferences.isBrowserQuickDl()) {
            webView.setOnLongClickListener(v -> {
                WebView.HitTestResult result = webView.getHitTestResult();

                String url = "";
                // Plain link
                if (result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE && result.getExtra() != null)
                    url = result.getExtra();

                // Image link (https://stackoverflow.com/a/55299801/8374722)
                if (result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    Handler handler = new Handler(getMainLooper());
                    Message message = handler.obtainMessage();

                    webView.requestFocusNodeHref(message);
                    url = message.getData().getString("url");
                }

                if (url != null && !url.isEmpty() && webClient.isGalleryPage(url)) {
                    // Launch on a new thread to avoid crashes
                    webClient.parseResponseAsync(url);
                    return true;
                } else {
                    return false;
                }
            });

            webView.setLongClickThreshold(Preferences.getBrowserQuickDlThreshold());
        }


        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        Timber.i("%s : using user-agent %s", getStartSite().name(), getStartSite().getUserAgent());
        webSettings.setUserAgentString(getStartSite().getUserAgent());

        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);

        if (fetchHandler != null)
            webView.addJavascriptInterface(new FetchHandler(fetchHandler), "fetchHandler");
    }

    private void initSwipeLayout() {
        CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        swipeLayout = findViewById(R.id.swipe_container);
        if (null == swipeLayout) return;

        swipeLayout.addView(webView, layoutParams);

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

    public void onPageStarted(String url, boolean isGalleryPage, boolean isHtmlLoaded, boolean isBookmarkable) {
        refreshStopMenu.setIcon(R.drawable.ic_close);
        progressBar.setVisibility(View.GONE);
        if (!isHtmlLoaded) {
            actionMenu.setIcon(downloadIcon);
            actionMenu.setEnabled(false);
        }

        // Activate fetch handler
        if (fetchHandler != null) {
            if (null == jsInterceptorScript) jsInterceptorScript = getJsInterceptorScript();
            webView.loadUrl(jsInterceptorScript);
        }

        // Display download button tooltip if a book page has been reached
        if (isGalleryPage) showTooltip(R.string.help_web_download, false);
        // Update bookmark button
        if (isBookmarkable) {
            List<SiteBookmark> bookmarks = objectBoxDAO.selectBookmarks(getStartSite());
            Optional<SiteBookmark> currentBookmark = Stream.of(bookmarks).filter(b -> SiteBookmark.urlsAreSame(b.getUrl(), url)).findFirst();
            updateBookmarkButton(currentBookmark.isPresent());
        }
    }

    // WARNING : This method may not be called from the UI thread
    public void onGalleryPageStarted() {
        blockedTags.clear();
        extraImages.clear();
        duplicateId = -1;
        duplicateSimilarity = 0f;
        // Cancel any previous extra page load
        EventBus.getDefault().post(new DownloadEvent(currentContent, DownloadEvent.Type.EV_INTERRUPT_CONTENT));
        // Greys out the action button
        // useful for sites with JS loading that do not trigger onPageStarted (e.g. Luscious, Pixiv)
        runOnUiThread(() -> {
            actionMenu.setIcon(downloadIcon);
            actionMenu.setEnabled(false);
        });
    }

    public void onPageFinished(boolean isResultsPage, boolean isGalleryPage) {
        refreshNavigationMenu(isResultsPage);
        refreshStopMenu.setIcon(R.drawable.ic_action_refresh);

        // Manage bottom alert banner visibility
        if (isGalleryPage)
            displayBottomAlertBanner(blockedTags); // Called here to be sure it is displayed on the gallery page
        else onBottomAlertCloseClick(null);
    }

    /**
     * Refresh the visuals of the buttons of the navigation menu
     */
    private void refreshNavigationMenu(boolean isResultsPage) {
        backMenu.setEnabled(webView.canGoBack());
        forwardMenu.setEnabled(webView.canGoForward());
        changeSeekMode(isResultsPage ? BaseWebActivity.SeekMode.PAGE : BaseWebActivity.SeekMode.GALLERY, isResultsPage || backListContainsGallery(webView.copyBackForwardList()) > -1);
    }

    /**
     * Displays the top alert banner
     * (the one that contains the alerts when downloads are broken or sites are unavailable)
     */
    private void displayTopAlertBanner() {
        if (topAlertMessage != null && alert != null) {
            topAlertIcon.setImageResource(alert.getStatus().getIcon());
            topAlertMessage.setText(formatAlertMessage(alert));
            topAlertBanner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Handler for the close icon of the top alert banner
     */
    public void onTopAlertCloseClick(View view) {
        topAlertBanner.setVisibility(View.GONE);
    }

    /**
     * Displays the bottom alert banner
     * (the one that contains the alerts when reaching a book with unwanted tags)
     */
    private void displayBottomAlertBanner(@NonNull final List<String> unwantedTags) {
        if (!unwantedTags.isEmpty()) {
            bottomAlertMessage.setText(getResources().getString(R.string.alert_unwanted_tags, TextUtils.join(", ", unwantedTags)));
            bottomAlertBanner.setVisibility(View.VISIBLE);
        } else {
            bottomAlertBanner.setVisibility(View.GONE);
        }
    }

    /**
     * Handler for the close icon of the bottom alert banner
     */
    public void onBottomAlertCloseClick(View view) {
        bottomAlertBanner.setVisibility(View.GONE);
    }


    /**
     * Handler for the "back" navigation button of the browser
     */
    private void onBackClick() {
        webView.goBack();
    }

    /**
     * Handler for the "forward" navigation button of the browser
     */
    private void onForwardClick() {
        webView.goForward();
    }

    /**
     * Handler for the "back to gallery page" / "seek page" navigation button of the browser
     */
    private void onSeekClick() {
        if (SeekMode.GALLERY == seekButtonMode) {
            WebBackForwardList list = webView.copyBackForwardList();
            int galleryIndex = backListContainsGallery(list);
            if (galleryIndex > -1) webView.goBackOrForward(galleryIndex - list.getCurrentIndex());
        } else { // Seek to page
            InputDialog.invokeNumberInputDialog(this, R.string.goto_page, this::goToPage);
        }
    }

    /**
     * Go to the given page number
     *
     * @param pageNum Page number to go to (1-indexed)
     */
    public void goToPage(int pageNum) {
        String url = webView.getUrl();
        if (pageNum < 1 || null == url) return;
        String newUrl = webClient.seekResultsUrl(url, pageNum);
        webView.loadUrl(newUrl);
    }

    /**
     * Handler for the "bookmark" top menu button of the browser
     */
    private void onBookmarkClick() {
        BookmarksDialogFragment.invoke(this, getStartSite(), StringHelper.protect(webView.getTitle()), StringHelper.protect(webView.getUrl()));
    }

    /**
     * Handler for the "refresh page/stop refreshing" button of the browser
     */
    private void onRefreshStopClick() {
        if (webClient.isLoading()) webView.stopLoading();
        else webView.reload();
    }

    /**
     * Handler for the "copy URL to clipboard" button
     */
    private void onCopyClick() {
        if (Helper.copyPlainTextToClipboard(this, StringHelper.protect(webView.getUrl())))
            ToastHelper.toast(R.string.web_url_clipboard);
    }

    /**
     * Handler for the "Home" navigation button
     */
    private void goHome() {
        Intent intent = new Intent(this, LibraryActivity.class);
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    public void openUrl(@NonNull final String url) {
        webView.loadUrl(url);
    }

    public void updateBookmarkButton(boolean newValue) {
        if (newValue) bookmarkMenu.setIcon(R.drawable.ic_bookmark_full);
        else bookmarkMenu.setIcon(R.drawable.ic_bookmark);
    }

    /**
     * Handler for the phone's back button
     */
    @Override
    public void onBackPressed() {
        if (!webView.canGoBack()) {
            goHome();
        }
    }

    /**
     * Listener for the Action button : download content, view queue or read content
     */
    protected void onActionClick() {
        boolean needsDuplicateAlert = Preferences.isDownloadDuplicateAsk() && duplicateSimilarity >= SIMILARITY_MIN_THRESHOLD;
        switch (actionButtonMode) {
            case ActionMode.DOWNLOAD:
                if (needsDuplicateAlert)
                    DuplicateDialogFragment.invoke(this, duplicateId, duplicateSimilarity, false);
                else processDownload(false, false);
                break;
            case ActionMode.DOWNLOAD_PLUS:
                if (needsDuplicateAlert)
                    DuplicateDialogFragment.invoke(this, duplicateId, duplicateSimilarity, true);
                else processDownload(false, true);
                break;
            case ActionMode.VIEW_QUEUE:
                goToQueue();
                break;
            case ActionMode.READ:
                if (currentContent != null) {
                    String searchUrl = getStartSite().hasCoverBasedPageUpdates() ? currentContent.getCoverImageUrl() : "";
                    currentContent = objectBoxDAO.selectContentBySourceAndUrl(currentContent.getSite(), currentContent.getUrl(), searchUrl);
                    if (currentContent != null && (StatusContent.DOWNLOADED == currentContent.getStatus()
                            || StatusContent.ERROR == currentContent.getStatus()
                            || StatusContent.MIGRATED == currentContent.getStatus()))
                        ContentHelper.openHentoidViewer(this, currentContent, -1, null, false);
                    else actionMenu.setEnabled(false);
                }
                break;
            default:
                // Nothing
        }
    }

    /**
     * Switch the action button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private void setActionMode(@ActionMode int mode) {
        @DrawableRes int resId = R.drawable.ic_info;
        if (ActionMode.DOWNLOAD == mode || ActionMode.DOWNLOAD_PLUS == mode) {
            resId = downloadIcon;
        } else if (ActionMode.VIEW_QUEUE == mode) {
            resId = R.drawable.ic_action_queue;
        } else if (ActionMode.READ == mode) {
            resId = R.drawable.ic_action_play;
        }
        BadgeDrawable badge = bottomToolbar.getOrCreateBadge(R.id.web_menu_action);
        badge.setVisible(ActionMode.DOWNLOAD_PLUS == mode);
        actionButtonMode = mode;
        actionMenu.setIcon(resId);
        actionMenu.setEnabled(true);
    }

    /**
     * Switch the seek button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private void changeSeekMode(@SeekMode int mode, boolean enabled) {
        @DrawableRes int resId = R.drawable.selector_back_gallery;
        if (SeekMode.PAGE == mode) resId = R.drawable.selector_page_seek;
        seekButtonMode = mode;
        seekMenu.setIcon(resId);
        seekMenu.setEnabled(enabled);
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     *
     * @param quickDownload True if the action has been triggered by a quick download
     *                      (which means we're not on a book gallery page but on the book list page)
     */
    void processDownload(boolean quickDownload, boolean isDownloadPlus) {
        if (null == currentContent) return;

        if (currentContent.getId() > 0)
            currentContent = objectBoxDAO.selectContent(currentContent.getId());

        if (null == currentContent) return;

        if (!isDownloadPlus && StatusContent.DOWNLOADED == currentContent.getStatus()) {
            ToastHelper.toast(R.string.already_downloaded);
            if (!quickDownload) setActionMode(ActionMode.READ);
            return;
        }

        if (isDownloadPlus) {
            // Copy the _current_ content's download params to the images
            String downloadParamsStr = currentContent.getDownloadParams();
            if (downloadParamsStr != null && downloadParamsStr.length() > 2) {
                for (ImageFile i : extraImages) i.setDownloadParams(downloadParamsStr);
            }

            // Determine base book : browsed downloaded book or best duplicate ?
            if (!ContentHelper.isInLibrary(currentContent.getStatus()) && duplicateId > 0) {
                currentContent = objectBoxDAO.selectContent(duplicateId);
                if (null == currentContent) return;
            }

            // Append additional pages to the base book's list of pages
            List<ImageFile> updatedImgs = new ArrayList<>(); // Entire image set to update
            Set<String> existingUrls = new HashSet<>(); // URLs of known images
            if (currentContent.getImageFiles() != null) {
                existingUrls.addAll(Stream.of(currentContent.getImageFiles()).map(ImageFile::getUrl).toList());
                updatedImgs.addAll(currentContent.getImageFiles());
            }

            // Save additional detected pages references to base book, without duplicate URLs
            List<ImageFile> additionalNonExistingImages = Stream.of(extraImages).filterNot(i -> existingUrls.contains(i.getUrl())).toList();
            if (!additionalNonExistingImages.isEmpty()) {
                updatedImgs.addAll(additionalNonExistingImages);
                currentContent.setImageFiles(updatedImgs);
            }

            currentContent.setStatus(StatusContent.SAVED);
            objectBoxDAO.insertContent(currentContent);
        }

        // Check if the tag blocker applies here
        List<String> blockedTagsLocal = ContentHelper.getBlockedTags(currentContent);
        if (!blockedTagsLocal.isEmpty()) {
            if (Preferences.getTagBlockingBehaviour() == Preferences.Constant.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE) { // Stop right here
                ToastHelper.toast(getResources().getString(R.string.blocked_tag, blockedTagsLocal.get(0)));
            } else { // Insert directly as an error
                List<ErrorRecord> errors = new ArrayList<>();
                errors.add(new ErrorRecord(ErrorType.BLOCKED, currentContent.getUrl(), "tags", "blocked tags : " + TextUtils.join(", ", blockedTagsLocal), Instant.now()));
                currentContent.setErrorLog(errors);
                currentContent.setStatus(StatusContent.ERROR);
                objectBoxDAO.insertContent(currentContent);
                ToastHelper.toast(getResources().getString(R.string.blocked_tag_queued, blockedTagsLocal.get(0)));
                setActionMode(ActionMode.VIEW_QUEUE);
            }
            return;
        }

        // No reason to block or ignore -> actually add to the queue
        if (Preferences.getQueueNewDownloadPosition() == QUEUE_NEW_DOWNLOADS_POSITION_ASK)
            AddQueueMenu.show(this, webView, this, (position, item) -> addToQueue((0 == position) ? QUEUE_NEW_DOWNLOADS_POSITION_TOP : QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM, Preferences.getBrowserDlAction()));
        else
            addToQueue(Preferences.getQueueNewDownloadPosition(), Preferences.getBrowserDlAction());
    }

    private void addToQueue(int position, int downloadMode) {
        animatedCheck.setVisibility(View.VISIBLE);
        ((Animatable) animatedCheck.getDrawable()).start();
        new Handler(getMainLooper()).postDelayed(() -> animatedCheck.setVisibility(View.GONE), 1000);
        currentContent.setDownloadMode(downloadMode);
        objectBoxDAO.addContentToQueue(currentContent, null, position, ContentQueueManager.getInstance().isQueueActive());
        if (Preferences.isQueueAutostart()) ContentQueueManager.getInstance().resumeQueue(this);
        setActionMode(ActionMode.VIEW_QUEUE);
    }

    /**
     * Take the user to the queue screen
     */
    private void goToQueue() {
        Intent intent = new Intent(this, QueueActivity.class);

        if (currentContent != null) {
            QueueActivityBundle.Builder builder = new QueueActivityBundle.Builder();
            builder.setContentHash(currentContent.uniqueHash());
            builder.setIsErrorsTab(currentContent.getStatus().equals(StatusContent.ERROR));
            intent.putExtras(builder.getBundle());
        }

        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            WebBackForwardList webBFL = webView.copyBackForwardList();
            String originalUrl = StringHelper.protect(webView.getOriginalUrl());
            int i = webBFL.getCurrentIndex();
            do {
                i--;
            }
            while (i >= 0 && originalUrl.equals(webBFL.getItemAtIndex(i).getOriginalUrl()));
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
     * @param onlineContent Currently displayed content
     * @return The status of the Content after being processed
     */
    private @ContentStatus
    int processContent(@NonNull Content onlineContent, boolean quickDownload) {
        Helper.assertNonUiThread();
        if (onlineContent.getUrl().isEmpty()) return ContentStatus.UNKNOWN;
        currentContent = null;

        Timber.i("Content Site, URL : %s, %s", onlineContent.getSite().getCode(), onlineContent.getUrl());
        String searchUrl = ""; //getStartSite().hasCoverBasedPageUpdates() ? content.getCoverImageUrl() : "";
        Content contentDB = objectBoxDAO.selectContentBySourceAndUrl(onlineContent.getSite(), onlineContent.getUrl(), searchUrl);

        boolean isInCollection = (contentDB != null && ContentHelper.isInLibrary(contentDB.getStatus()));
        boolean isInQueue = (contentDB != null && ContentHelper.isInQueue(contentDB.getStatus()));

        if (!isInCollection && !isInQueue) {
            if (Preferences.isDownloadDuplicateAsk() && !onlineContent.getCoverImageUrl().isEmpty()) {
                // Index the content's cover picture
                long pHash = Long.MIN_VALUE;
                try {
                    List<Pair<String, String>> requestHeadersList = new ArrayList<>();

                    Map<String, String> downloadParams = ContentHelper.parseDownloadParams(onlineContent.getDownloadParams());
                    downloadParams.put(HttpHelper.HEADER_COOKIE_KEY, HttpHelper.getCookies(onlineContent.getCoverImageUrl()));
                    downloadParams.put(HttpHelper.HEADER_REFERER_KEY, onlineContent.getSite().getUrl());

                    Response onlineCover = HttpHelper.getOnlineResource(
                            HttpHelper.fixUrl(onlineContent.getCoverImageUrl(), getStartUrl()),
                            requestHeadersList,
                            getStartSite().useMobileAgent(),
                            getStartSite().useHentoidAgent(),
                            getStartSite().useWebviewAgent()
                    );
                    ResponseBody coverBody = onlineCover.body();
                    if (coverBody != null) {
                        InputStream bodyStream = coverBody.byteStream();
                        Bitmap b = DuplicateHelper.Companion.getCoverBitmapFromStream(bodyStream);
                        pHash = DuplicateHelper.Companion.calcPhash(DuplicateHelper.Companion.getHashEngine(), b);
                    }
                } catch (IOException e) {
                    Timber.w(e);
                }
                // Look for duplicates
                try {
                    ImmutablePair<Content, Float> duplicateResult = ContentHelper.findDuplicate(this, onlineContent, pHash, objectBoxDAO);
                    if (duplicateResult != null) {
                        duplicateId = duplicateResult.left.getId();
                        duplicateSimilarity = duplicateResult.right;
                        // Content ID of the duplicate candidate of the currently viewed Content
                        boolean duplicateSameSite = duplicateResult.left.getSite().equals(onlineContent.getSite());
                        // Same site and similar => download by default, but look for extra pics just in case
                        if (duplicateSameSite && Preferences.isDownloadPlusDuplicateTry() && !quickDownload)
                            searchForExtraImages(duplicateResult.left, onlineContent);
                    }
                } catch (Exception e) {
                    Timber.w(e);
                }
            }

            if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB
                onlineContent.setStatus(StatusContent.SAVED);
                ContentHelper.addContent(this, objectBoxDAO, onlineContent);
            } else {
                currentContent = contentDB;
            }
        } else {
            currentContent = contentDB;
        }

        if (null == currentContent) currentContent = onlineContent;

        if (isInCollection) {
            if (!quickDownload) searchForExtraImages(contentDB, onlineContent);
            return ContentStatus.IN_COLLECTION;
        }
        if (isInQueue) return ContentStatus.IN_QUEUE;
        return ContentStatus.UNKNOWN;
    }

    public void onResultReady(@NonNull Content result, boolean quickDownload) {
        if (processContentDisposable != null)
            processContentDisposable.dispose(); // Cancel whichever process was happening before
        processContentDisposable = Single.fromCallable(() -> processContent(result, quickDownload))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        status -> onContentProcessed(status, quickDownload),
                        t -> {
                            Timber.e(t);
                            onContentProcessed(ContentStatus.UNKNOWN, false);
                        }
                );
    }

    private void onContentProcessed(@ContentStatus int status, boolean quickDownload) {
        processContentDisposable.dispose();
        switch (status) {
            case ContentStatus.UNKNOWN:
                if (quickDownload) {
                    if (duplicateId > -1 && Preferences.isDownloadDuplicateAsk())
                        DuplicateDialogFragment.invoke(this, duplicateId, duplicateSimilarity, false);
                    else
                        processDownload(true, false);
                } else setActionMode(ActionMode.DOWNLOAD);
                break;
            case ContentStatus.IN_COLLECTION:
                if (quickDownload) ToastHelper.toast(R.string.already_downloaded);
                setActionMode(ActionMode.READ);
                break;
            case ContentStatus.IN_QUEUE:
                if (quickDownload) ToastHelper.toast(R.string.already_queued);
                setActionMode(ActionMode.VIEW_QUEUE);
                break;
            default:
                // Nothing
        }
        blockedTags = ContentHelper.getBlockedTags(currentContent);
    }

    public void onResultFailed() {
        runOnUiThread(() -> ToastHelper.toast(R.string.web_unparsable));
    }

    private void searchForExtraImages(
            @NonNull final Content storedContent,
            @NonNull final Content onlineContent) {
        if (searchExtraImagesdisposable != null)
            searchExtraImagesdisposable.dispose(); // Cancel previous operation
        searchExtraImagesdisposable = Single.fromCallable(() -> doSearchForExtraImages(storedContent, onlineContent))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        list -> onSearchForExtraImagesSuccess(storedContent, onlineContent, list),
                        Timber::w
                );
    }

    private List<ImageFile> doSearchForExtraImages(@NonNull final Content storedContent, @NonNull final Content onlineContent) throws Exception {
        List<ImageFile> result = Collections.emptyList();

        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(onlineContent);
        // Call the parser to retrieve all the pages
        // Progress bar on browser UI is refreshed through onDownloadPreparationEvent
        List<ImageFile> onlineImgs = parser.parseImageList(onlineContent, storedContent);
        if (onlineImgs.isEmpty()) return result;

        int maxStoredImageOrder = 0;
        if (storedContent.getImageFiles() != null) {
            Optional<Integer> opt = Stream.of(storedContent.getImageFiles()).filter(i -> ContentHelper.isInLibrary(i.getStatus())).map(ImageFile::getOrder).max(Integer::compareTo);
            if (opt.isPresent()) maxStoredImageOrder = opt.get();
        }
        final int maxStoredImageOrderFinal = maxStoredImageOrder;

        // Attach chapters to books downloaded before chapters were implemented
        int maxOnlineImageOrder = 0;
        int minOnlineImageOrder = Integer.MAX_VALUE;
        Map<Integer, Chapter> positionMap = new HashMap<>();
        for (ImageFile img : onlineImgs) {
            maxOnlineImageOrder = Math.max(maxOnlineImageOrder, img.getOrder());
            minOnlineImageOrder = Math.min(minOnlineImageOrder, img.getOrder());
            if (null != img.getLinkedChapter())
                positionMap.put(img.getOrder(), img.getLinkedChapter());
        }

        List<Chapter> storedChapters = storedContent.getChapters();
        if (!positionMap.isEmpty() && minOnlineImageOrder < maxStoredImageOrder && (null == storedChapters || storedChapters.isEmpty())) {
            // Attach chapters to stored images
            List<ImageFile> storedImages = storedContent.getImageFiles();
            if (null == storedImages) storedImages = Collections.emptyList();
            for (ImageFile img : storedImages) {
                if (null == img.getLinkedChapter()) {
                    Chapter targetChapter = positionMap.get(img.getOrder());
                    if (targetChapter != null) img.setChapter(targetChapter);
                }
            }
            objectBoxDAO.insertImageFiles(storedImages);
        }

        // Online book has more pictures than stored book -> that's what we're looking for
        if (maxOnlineImageOrder > maxStoredImageOrder) {
            return Stream.of(onlineImgs).filter(i -> i.getOrder() > maxStoredImageOrderFinal).distinct().toList();
        }
        return result;
    }

    private void onSearchForExtraImagesSuccess(
            @NonNull final Content storedContent,
            @NonNull final Content onlineContent,
            @NonNull final List<ImageFile> additionalImages) {
        searchExtraImagesdisposable.dispose();
        progressBar.setProgress(progressBar.getMax());
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgressTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));

        if (null == currentContent || additionalImages.isEmpty()) return;

        if (currentContent.getUrl().equalsIgnoreCase(onlineContent.getUrl()) || duplicateId == storedContent.getId()) { // User hasn't left the book page since
            // Retrieve the URLs of stored pages
            Set<String> storedUrls = new HashSet<>();
            if (storedContent.getImageFiles() != null) {
                storedUrls.addAll(Stream.of(storedContent.getImageFiles()).filter(i -> ContentHelper.isInLibrary(i.getStatus())).map(ImageFile::getUrl).toList());
            }

            // Display the "download more" button only if extra images URLs aren't duplicates
            List<ImageFile> additionalNonDownloadedImages = Stream.of(additionalImages).filterNot(i -> storedUrls.contains(i.getUrl())).toList();
            if (!additionalNonDownloadedImages.isEmpty()) {
                extraImages = additionalNonDownloadedImages;
                setActionMode(ActionMode.DOWNLOAD_PLUS);
                BadgeDrawable badge = bottomToolbar.getOrCreateBadge(R.id.web_menu_action);
                badge.setNumber(additionalNonDownloadedImages.size());
            }
        }
    }

    private void updateSiteGalleries() {
        synchronized (siteGalleries) {
            siteGalleries.clear();
            siteGalleries.addAll(
                    Stream.of(objectBoxDAO.selectAllSourceUrls(getStartSite()))
                            .map(s -> s.replaceAll("\\p{Punct}", "."))
                            .map(s -> s.endsWith(".") && s.length() > 1 ? s.substring(0, s.length() - 1) : s)
                            .toList()
            );
        }
    }

    /**
     * Listener for the events of the download engine
     * Used to switch the action button to Read when the download of the currently viewed is completed
     *
     * @param event Event fired by the download engine
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.Type.EV_COMPLETE) {
            //TODO condition
            updateSiteGalleries();
            if (event.content != null && event.content.equals(currentContent) && event.content.getStatus().equals(StatusContent.DOWNLOADED)) {
                setActionMode(ActionMode.READ);
            }
        }
    }

    void showTooltip(@StringRes int resource, boolean always) {
        TooltipHelper.showTooltip(this, resource, ArrowOrientation.BOTTOM, bottomToolbar, this, always);
    }

    @Override
    public void onDownloadDuplicate(boolean isDownloadPlus) {
        processDownload(false, isDownloadPlus);
    }


    /**
     * Indicate if the browser's back list contains a book gallery
     * Used to determine the display of the "back to latest gallery" button
     *
     * @param backForwardList Back list to examine
     * @return Index of the latest book gallery in the list; -1 if none has been detected
     */
    private int backListContainsGallery(@NonNull final WebBackForwardList backForwardList) {
        for (int i = backForwardList.getCurrentIndex() - 1; i >= 0; i--) {
            WebHistoryItem item = backForwardList.getItemAtIndex(i);
            if (webClient.isGalleryPage(item.getUrl())) return i;
        }
        return -1;
    }

    /**
     * Format the message to display for the given source alert
     *
     * @param alert Source alert
     * @return Message to be displayed for the user for the given source alert
     */
    private String formatAlertMessage(@NonNull final UpdateInfo.SourceAlert alert) {
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

    /**
     * Show the viewer settings dialog
     */
    private void onSettingsClick() {
        Intent intent = new Intent(this, PrefsActivity.class);

        PrefsActivityBundle.Builder builder = new PrefsActivityBundle.Builder();
        builder.setIsBrowserPrefs(true);
        intent.putExtras(builder.getBundle());

        startActivity(intent);
    }

    @Override
    public List<String> getAllSiteUrls() {
        return new ArrayList<>(siteGalleries); // Work on a copy to avoid any thread-synch issue
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param prefs Shared preferences object
     * @param key   Key that has been changed
     */
    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Preferences.Key.BROWSER_DL_ACTION.equals(key)) {
            downloadIcon = (Preferences.getBrowserDlAction() == Content.DownloadMode.DOWNLOAD) ? R.drawable.selector_download_action : R.drawable.selector_download_stream_action;
            setActionMode(actionButtonMode);
        }
    }

    private String getJsInterceptorScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("javascript:");
        try (InputStream is = getAssets().open("fetch_override.js"); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                sb.append(sCurrentLine);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return sb.toString();
    }

    // References :
    // https://stackoverflow.com/a/64961272/8374722
    // https://stackoverflow.com/questions/3941969/android-intercept-ajax-call-from-webview/5742194
    public static class FetchHandler {

        private final BiConsumer<String, String> handler;

        public FetchHandler(BiConsumer<String, String> handler) {
            this.handler = handler;
        }

        @JavascriptInterface
        public void onFetchCall(String url, String body) {
            Timber.w("AJAX Begin %s : %s", url, body);
            handler.accept(url, body);
        }
    }
}
