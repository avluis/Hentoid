package me.devsaki.hentoid.activities.sources;

import static me.devsaki.hentoid.util.Preferences.Constant.DL_ACTION_ASK;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK;
import static me.devsaki.hentoid.util.file.PermissionHelper.RQST_STORAGE_PERMISSION;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
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

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.BiConsumer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.skydoves.balloon.ArrowOrientation;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.objectbox.relation.ToOne;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.BaseActivity;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.MissingWebViewActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.PrefsBundle;
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.databinding.ActivityBaseWebBinding;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadCommandEvent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.fragments.web.BookmarksDialogFragment;
import me.devsaki.hentoid.fragments.web.DuplicateDialogFragment;
import me.devsaki.hentoid.json.core.UpdateInfo;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.DuplicateHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.TooltipHelper;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.file.PermissionHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.network.WebkitPackageHelper;
import me.devsaki.hentoid.views.NestedScrollWebView;
import me.devsaki.hentoid.widget.AddQueueMenu;
import me.devsaki.hentoid.widget.DownloadModeMenu;
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

    @IntDef({ContentStatus.UNDOWNLOADABLE, ContentStatus.UNKNOWN, ContentStatus.IN_COLLECTION, ContentStatus.IN_QUEUE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ContentStatus {
        // Content is undownloadable
        int UNDOWNLOADABLE = -1;
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
    private CollectionDAO dao;
    // Disposable to be used for parsing response during quick search
    private final CompositeDisposable parseResponseDisposable = new CompositeDisposable();
    // Disposable to be used for punctual search
    private Disposable searchExtraImagesDisposable;
    // Disposable to be used for content processing
    private Disposable processContentDisposable;
    // Disposable to be used for content processing
    protected Disposable extraProcessingDisposable;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;

    // === UI
    private ActivityBaseWebBinding binding;
    // Dynamically generated webview
    protected NestedScrollWebView webView;
    // Top toolbar buttons
    private MenuItem refreshStopMenu;
    private MenuItem bookmarkMenu;
    private @DrawableRes
    int downloadIcon;
    protected FloatingActionButton languageFilterButton;

    // === CURRENTLY VIEWED CONTENT-RELATED VARIABLES
    private Content currentContent = null;
    // Content ID of the duplicate candidate of the currently viewed Content
    private long duplicateId = -1;
    // Similarity score of the duplicate candidate of the currently viewed Content
    private float duplicateSimilarity = 0f;
    // Title of the browsed content; valued if extra images have been detected
    private String onlineContentTitle = "";
    // Blocked tags found on the currently viewed Content
    private List<String> blockedTags = Collections.emptyList();
    // Extra images found on the currently viewed Content
    private List<ImageFile> extraImages = Collections.emptyList();
    // List of URLs of downloaded books for the current site
    private final List<String> downloadedBooksUrls = new ArrayList<>();
    // List of URLs of merged books for the current site
    private final List<String> mergedBooksUrls = new ArrayList<>();
    // List of tags of Preference-browser-blocked tags
    private List<String> prefBlockedTags = new ArrayList<>();

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
    protected BiConsumer<String, String> xhrHandler = null;
    protected String jsInterceptorScript = null;
    protected String customCss = null;


    protected abstract CustomWebViewClient createWebClient();

    abstract Site getStartSite();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBaseWebBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!WebkitPackageHelper.getWebViewAvailable()) {
            startActivity(new Intent(this, MissingWebViewActivity.class));
            return;
        }

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        dao = new ObjectBoxDAO(this);
        Preferences.registerPrefsChangedListener(listener);

        if (Preferences.isBrowserMarkDownloaded()) updateDownloadedBooksUrls();
        if (Preferences.isBrowserMarkMerged()) updateMergedBooksUrls();
        if (Preferences.isBrowserMarkBlockedTags()) updatePrefBlockedTags();

        if (getStartSite() == null) {
            Timber.w("Site is null!");
        } else {
            Timber.d("Loading site: %s", getStartSite());
        }

        // Toolbar
        // Top toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        Helper.tryShowMenuIcons(this, toolbar.getMenu());
        toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
        toolbar.setTitle(getStartSite().getDescription());
        toolbar.setOnClickListener(v -> loadUrl(getStartSite().getUrl()));
        refreshStopMenu = toolbar.getMenu().findItem(R.id.web_menu_refresh_stop);
        bookmarkMenu = toolbar.getMenu().findItem(R.id.web_menu_bookmark);
        languageFilterButton = findViewById(R.id.language_filter_button);
        binding.bottomNavigation.setOnMenuItemClickListener(this::onMenuItemSelected);
        binding.menuHome.setOnClickListener(v -> goHome());
        binding.menuSeek.setOnClickListener(v -> onSeekClick());
        binding.menuBack.setOnClickListener(v -> onBackClick());
        binding.menuForward.setOnClickListener(v -> onForwardClick());
        binding.actionButton.setOnClickListener(v -> onActionClick());

        // Webview
        initWebview();
        initSwipeLayout();
        webView.loadUrl(getStartUrl());

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        // Banner close buttons
        View topAlertCloseButton = findViewById(R.id.top_alert_close_btn);
        topAlertCloseButton.setOnClickListener(this::onTopAlertCloseClick);
        View bottomAlertCloseButton = findViewById(R.id.bottom_alert_close_btn);
        bottomAlertCloseButton.setOnClickListener(this::onBottomAlertCloseClick);

        downloadIcon = (Preferences.getBrowserDlAction() == Content.DownloadMode.STREAM) ? R.drawable.selector_download_stream_action : R.drawable.selector_download_action;
        if (Preferences.isBrowserMode()) downloadIcon = R.drawable.ic_forbidden_disabled;
        binding.actionButton.setImageDrawable(ContextCompat.getDrawable(this, downloadIcon));

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
            BaseWebActivityBundle bundle = new BaseWebActivityBundle(getIntent().getExtras());
            String intentUrl = StringHelper.protect(bundle.getUrl());
            if (!intentUrl.isEmpty()) return intentUrl;
        }

        // Priority 2 : Last viewed position, if option enabled
        if (Preferences.isBrowserResumeLast()) {
            SiteHistory siteHistory = dao.selectHistory(getStartSite());
            if (siteHistory != null && !siteHistory.getUrl().isEmpty()) return siteHistory.getUrl();
        }

        // Priority 3 : Homepage, if manually set through bookmarks
        SiteBookmark welcomePage = dao.selectHomepage(getStartSite());
        if (welcomePage != null) return welcomePage.getUrl();

        // Default site URL
        return getStartSite().getUrl();
    }

    @SuppressLint("NonConstantResourceId")
    private boolean onMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.web_menu_bookmark:
                this.onBookmarkClick();
                break;
            case R.id.web_menu_refresh_stop:
                this.onRefreshStopClick();
                break;
            case R.id.web_menu_copy:
                this.onCopyClick();
                break;
            case R.id.web_menu_settings:
                this.onSettingsClick();
                break;
            case R.id.web_menu_about:
                this.onAboutClick();
                break;
            default:
                return false;
        }
        return true;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void onUpdateEvent(UpdateEvent event) {
        if (event.sourceAlerts.containsKey(getStartSite())) {
            alert = event.sourceAlerts.get(getStartSite());
            displayTopAlertBanner();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void onDownloadPreparationEvent(DownloadPreparationEvent event) {
        // Show progress if it's about current content or its best duplicate
        if (
                (currentContent != null && ContentHelper.isInLibrary(currentContent.getStatus()) && event.getRelevantId() == currentContent.getId())
                        || (duplicateId > 0 && event.getRelevantId() == duplicateId)
        ) {
            binding.progressBar.setMax(event.total);
            binding.progressBar.setProgress(event.done);
            binding.progressBar.setProgressTintList(ColorStateList.valueOf(getResources().getColor(R.color.secondary_light)));
            binding.progressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        BaseWebActivityBundle bundle = new BaseWebActivityBundle();
        if (WebkitPackageHelper.getWebViewAvailable()) bundle.setUrl(webView.getUrl());
        outState.putAll(bundle.getBundle());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        String url = new BaseWebActivityBundle(savedInstanceState).getUrl();
        if (!url.isEmpty()) webView.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!WebkitPackageHelper.getWebViewAvailable()) {
            startActivity(new Intent(this, MissingWebViewActivity.class));
            return;
        }

        checkPermissions();
        String url = webView.getUrl();
        Timber.i(">> WebActivity resume : %s %s %s", url, currentContent != null, (currentContent != null) ? currentContent.getTitle() : "");
        if (currentContent != null && url != null && createWebClient().isGalleryPage(url)) {
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
        if (WebkitPackageHelper.getWebViewAvailable() && (webView.getUrl() != null))
            dao.insertSiteHistory(getStartSite(), webView.getUrl());
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
        EventBus.getDefault().post(new DownloadCommandEvent(currentContent, DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT));

        if (dao != null) dao.cleanup();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        if (parseResponseDisposable != null) parseResponseDisposable.dispose();
        if (searchExtraImagesDisposable != null) searchExtraImagesDisposable.dispose();
        if (processContentDisposable != null) processContentDisposable.dispose();
        if (extraProcessingDisposable != null) extraProcessingDisposable.dispose();

        binding = null;
        super.onDestroy();
    }

    // Make sure permissions are set at resume time; if not, warn the user
    private void checkPermissions() {
        if (Preferences.isBrowserMode()) return;
        if (!PermissionHelper.INSTANCE.requestExternalStorageReadWritePermission(this, RQST_STORAGE_PERMISSION))
            ToastHelper.toast(R.string.web_storage_permission_denied);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebview() {
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
                if (binding != null) {
                    if (newProgress == 100) {
                        binding.swipeContainer.post(() -> {
                            if (binding != null) binding.swipeContainer.setRefreshing(false);
                        });
                    } else {
                        binding.swipeContainer.post(() -> {
                            if (binding != null) binding.swipeContainer.setRefreshing(true);
                        });
                    }
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
            webView.setInitialScale(Preferences.Default.WEBVIEW_INITIAL_ZOOM);
            webView.getSettings().setLoadWithOverviewMode(true);
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);


        webClient = createWebClient();
        webView.setWebViewClient(webClient);

        // Download immediately on long click on a link / image link
        if (Preferences.isBrowserQuickDl()) {
            webView.setOnLongTapListener(this::onLongTap);
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
        if (xhrHandler != null)
            webView.addJavascriptInterface(new XhrHandler(xhrHandler), "xhrHandler");
    }

    private void initSwipeLayout() {
        CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        binding.swipeContainer.addView(webView, layoutParams);

        binding.swipeContainer.setOnRefreshListener(() -> {
            if (!binding.swipeContainer.isRefreshing() || !webClient.isLoading()) webView.reload();
        });
        binding.swipeContainer.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    private void onLongTap(@NonNull Integer x, @NonNull Integer y) {
        if (Preferences.isBrowserMode()) return;
        WebView.HitTestResult result = webView.getHitTestResult();

        final String url;
        // Plain link
        if (result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE && result.getExtra() != null) {
            url = result.getExtra();
        }
        // Image link (https://stackoverflow.com/a/55299801/8374722)
        else if (result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            Handler handler = new Handler(getMainLooper());
            Message message = handler.obtainMessage();

            webView.requestFocusNodeHref(message);
            url = message.getData().getString("url");
        } else {
            url = null;
        }

        if (url != null && !url.isEmpty() && webClient.isGalleryPage(url)) {
            Helper.setMargins(binding.quickDlFeedback,
                    x - binding.quickDlFeedback.getWidth() / 2,
                    y - (binding.quickDlFeedback.getHeight() / 2) + binding.topBar.getBottom(), 0, 0);
            binding.quickDlFeedback.setIndicatorColor(getResources().getColor(R.color.medium_gray));
            binding.quickDlFeedback.setVisibility(View.VISIBLE);

            // Run on a new thread to avoid crashes
            parseResponseDisposable.add(Single.fromCallable(() -> webClient.parseResponseOptional(url, null, true, true))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(res -> {
                        if (res.isEmpty()) {
                            binding.quickDlFeedback.setVisibility(View.INVISIBLE);
                        } else {
                            binding.quickDlFeedback.setIndicatorColor(ThemeHelper.getColor(this, R.color.secondary_light));
                        }
                    }, Timber::e));
        }
    }

    public void onPageStarted(
            String url,
            boolean isGalleryPage,
            boolean isHtmlLoaded,
            boolean isBookmarkable,
            List<String> jsStartupScripts) {
        refreshStopMenu.setIcon(R.drawable.ic_close);
        binding.progressBar.setVisibility(View.GONE);
        if (!isHtmlLoaded) disableActions();

        // Activate fetch handler
        if (fetchHandler != null) {
            if (null == jsInterceptorScript) jsInterceptorScript = getJsScript("fetch_override.js");
            webView.loadUrl(jsInterceptorScript);
        }
        // Activate XHR handler
        if (xhrHandler != null) {
            if (null == jsInterceptorScript) jsInterceptorScript = getJsScript("xhr_override.js");
            webView.loadUrl(jsInterceptorScript);
        }

        // Activate startup JS
        if (jsStartupScripts != null) {
            for (String s : jsStartupScripts)
                webView.loadUrl(getJsScript(s));
        }

        // Display download button tooltip if a book page has been reached
        if (isGalleryPage && !Preferences.isBrowserMode())
            showTooltip(R.string.help_web_download, false);
        // Update bookmark button
        if (isBookmarkable) {
            List<SiteBookmark> bookmarks = dao.selectBookmarks(getStartSite());
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
        EventBus.getDefault().post(new DownloadCommandEvent(currentContent, DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT));
        // Greys out the action button
        // useful for sites with JS loading that do not trigger onPageStarted (e.g. Luscious, Pixiv)
        runOnUiThread(() -> {
            if (binding != null) {
                binding.actionButton.setImageDrawable(ContextCompat.getDrawable(this, downloadIcon));
                binding.actionButton.setVisibility(View.INVISIBLE);
                binding.actionBtnBadge.setVisibility(View.INVISIBLE);
            }
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
        binding.menuBack.setEnabled(webView.canGoBack());
        binding.menuForward.setEnabled(webView.canGoForward());
        changeSeekMode(isResultsPage ? BaseWebActivity.SeekMode.PAGE : BaseWebActivity.SeekMode.GALLERY, isResultsPage || backListContainsGallery(webView.copyBackForwardList()) > -1);
    }

    /**
     * Displays the top alert banner
     * (the one that contains the alerts when downloads are broken or sites are unavailable)
     */
    private void displayTopAlertBanner() {
        if (alert != null) {
            binding.topAlertIcon.setImageResource(alert.getStatus().getIcon());
            binding.topAlertTxt.setText(formatAlertMessage(alert));
            binding.topAlert.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Handler for the close icon of the top alert banner
     */
    public void onTopAlertCloseClick(View view) {
        binding.topAlert.setVisibility(View.GONE);
    }

    /**
     * Displays the bottom alert banner
     * (the one that contains the alerts when reaching a book with unwanted tags)
     */
    private void displayBottomAlertBanner(@NonNull final List<String> unwantedTags) {
        if (!unwantedTags.isEmpty()) {
            binding.bottomAlertTxt.setText(getResources().getString(R.string.alert_unwanted_tags, TextUtils.join(", ", unwantedTags)));
            binding.bottomAlert.setVisibility(View.VISIBLE);
        } else {
            binding.bottomAlert.setVisibility(View.GONE);
        }
    }

    /**
     * Handler for the close icon of the bottom alert banner
     */
    public void onBottomAlertCloseClick(View view) {
        binding.bottomAlert.setVisibility(View.GONE);
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

    public void loadUrl(@NonNull final String url) {
        if (webView != null) webView.loadUrl(url);
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
        if (null == currentContent) return;
        boolean needsDuplicateAlert = Preferences.isDownloadDuplicateAsk() && duplicateSimilarity >= SIMILARITY_MIN_THRESHOLD;
        switch (actionButtonMode) {
            case ActionMode.DOWNLOAD:
                if (needsDuplicateAlert)
                    DuplicateDialogFragment.invoke(this, duplicateId, currentContent.getQtyPages(), duplicateSimilarity, false);
                else processDownload(false, false, false);
                break;
            case ActionMode.DOWNLOAD_PLUS:
                if (needsDuplicateAlert)
                    DuplicateDialogFragment.invoke(this, duplicateId, currentContent.getQtyPages(), duplicateSimilarity, true);
                else processDownload(false, true, false);
                break;
            case ActionMode.VIEW_QUEUE:
                goToQueue();
                break;
            case ActionMode.READ:
                String searchUrl = getStartSite().hasCoverBasedPageUpdates() ? currentContent.getCoverImageUrl() : "";
                currentContent = dao.selectContentBySourceAndUrl(currentContent.getSite(), currentContent.getUrl(), searchUrl);
                if (currentContent != null && (StatusContent.DOWNLOADED == currentContent.getStatus()
                        || StatusContent.ERROR == currentContent.getStatus()
                        || StatusContent.MIGRATED == currentContent.getStatus()))
                    ContentHelper.openReader(this, currentContent, -1, null, false, false);
                else {
                    binding.actionButton.setVisibility(View.INVISIBLE);
                    binding.actionBtnBadge.setVisibility(View.INVISIBLE);
                }
                break;
            default:
                // Nothing
        }
    }

    private void disableActions() {
        final ActivityBaseWebBinding b = binding;
        if (b != null) {
            b.actionButton.setImageDrawable(ContextCompat.getDrawable(this, downloadIcon));
            b.actionButton.setVisibility(View.INVISIBLE);
            b.actionBtnBadge.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Switch the action button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private void setActionMode(@ActionMode int mode) {
        final ActivityBaseWebBinding b = binding;
        if (Preferences.isBrowserMode() && b != null) {
            b.actionButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_forbidden_disabled));
            b.actionButton.setVisibility(View.INVISIBLE);
            b.actionBtnBadge.setVisibility(View.INVISIBLE);
            return;
        }

        @DrawableRes int resId = R.drawable.ic_info;
        if (ActionMode.DOWNLOAD == mode || ActionMode.DOWNLOAD_PLUS == mode) {
            resId = downloadIcon;
        } else if (ActionMode.VIEW_QUEUE == mode) {
            resId = R.drawable.ic_action_queue;
        } else if (ActionMode.READ == mode) {
            resId = R.drawable.ic_action_play;
        }
        actionButtonMode = mode;
        if (b != null) {
            b.actionButton.setImageDrawable(ContextCompat.getDrawable(this, resId));
            b.actionButton.setVisibility(View.VISIBLE);
            // It will become visible whenever the count of extra pages is known
            if (ActionMode.DOWNLOAD_PLUS != mode)
                b.actionBtnBadge.setVisibility(View.INVISIBLE);
        }
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
        final ActivityBaseWebBinding b = binding;
        if (b != null) {
            b.menuSeek.setImageDrawable(ContextCompat.getDrawable(this, resId));
            b.menuSeek.setEnabled(enabled);
        }
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     *
     * @param quickDownload      True if the action has been triggered by a quick download
     *                           (which means we're not on a book gallery page but on the book list page)
     * @param isDownloadPlus     True if the action has been triggered by a "download extra pages" action
     * @param isReplaceDuplicate True if the action has been triggered by a "download and replace existing duplicate book" action
     */
    void processDownload(boolean quickDownload, boolean isDownloadPlus, boolean isReplaceDuplicate) {
        if (null == currentContent) return;

        if (currentContent.getId() > 0)
            currentContent = dao.selectContent(currentContent.getId());

        if (null == currentContent) return;

        if (!isDownloadPlus && StatusContent.DOWNLOADED == currentContent.getStatus()) {
            ToastHelper.toast(R.string.already_downloaded);
            if (!quickDownload) setActionMode(ActionMode.READ);
            return;
        }

        String replacementTitle = null;
        if (isDownloadPlus) {
            // Copy the _current_ content's download params to the extra images
            String downloadParamsStr = currentContent.getDownloadParams();
            if (downloadParamsStr != null && downloadParamsStr.length() > 2) {
                for (ImageFile i : extraImages) i.setDownloadParams(downloadParamsStr);
            }

            // Determine base book : browsed downloaded book or best duplicate ?
            if (!ContentHelper.isInLibrary(currentContent.getStatus()) && duplicateId > 0) {
                currentContent = dao.selectContent(duplicateId);
                if (null == currentContent) return;
            }

            // Append additional pages & chapters to the base book's list of pages & chapters
            List<ImageFile> updatedImgs = new ArrayList<>(); // Entire image set to update
            Set<String> existingImageUrls = new HashSet<>(); // URLs of known images
            Set<Integer> existingChapterOrders = new HashSet<>(); // Positions of known chapters
            if (currentContent.getImageFiles() != null) {
                existingImageUrls.addAll(Stream.of(currentContent.getImageFiles()).map(ImageFile::getUrl).toList());
                existingChapterOrders.addAll(Stream.of(currentContent.getImageFiles()).map(i -> {
                    if (null == i.getChapter()) return -1;
                    if (null == i.getChapter().getTarget()) return -1;
                    return i.getChapter().getTarget().getOrder();
                }).toList());
                updatedImgs.addAll(currentContent.getImageFiles());
            }

            // Save additional pages references to stored book, without duplicate URLs
            List<ImageFile> additionalNonExistingImages = Stream.of(extraImages).filterNot(i -> existingImageUrls.contains(i.getUrl())).toList();
            if (!additionalNonExistingImages.isEmpty()) {
                updatedImgs.addAll(additionalNonExistingImages);
                currentContent.setImageFiles(updatedImgs);
                // Update content title if extra pages are found and title has changed
                if (!StringHelper.protect(onlineContentTitle).isEmpty() && !onlineContentTitle.equalsIgnoreCase(currentContent.getTitle()))
                    replacementTitle = onlineContentTitle;
            }
            // Save additional chapters to stored book
            List<Chapter> additionalNonExistingChapters = Stream.of(additionalNonExistingImages)
                    .map(ImageFile::getChapter).withoutNulls()
                    .map(ToOne::getTarget).withoutNulls()
                    .filterNot(c -> existingChapterOrders.contains(c.getOrder())).toList();
            if (!additionalNonExistingChapters.isEmpty()) {
                List<Chapter> updatedChapters;
                if (currentContent.getChapters() != null)
                    updatedChapters = new ArrayList<>(currentContent.getChapters());
                else
                    updatedChapters = new ArrayList<>();
                updatedChapters.addAll(additionalNonExistingChapters);
                currentContent.setChapters(updatedChapters);
            }

            currentContent.setStatus(StatusContent.SAVED);
            dao.insertContent(currentContent);
        } // isDownloadPlus

        // Check if the tag blocker applies here
        List<String> blockedTagsLocal = ContentHelper.getBlockedTags(currentContent);
        if (!blockedTagsLocal.isEmpty()) {
            if (Preferences.getTagBlockingBehaviour() == Preferences.Constant.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE) { // Stop right here
                ToastHelper.toast(R.string.blocked_tag, blockedTagsLocal.get(0));
            } else { // Insert directly as an error
                List<ErrorRecord> errors = new ArrayList<>();
                errors.add(new ErrorRecord(ErrorType.BLOCKED, currentContent.getUrl(), "tags", "blocked tags : " + TextUtils.join(", ", blockedTagsLocal), Instant.now()));
                currentContent.setErrorLog(errors);
                currentContent.setDownloadMode(Preferences.getBrowserDlAction());
                currentContent.setStatus(StatusContent.ERROR);
                if (isReplaceDuplicate) currentContent.setContentIdToReplace(duplicateId);
                dao.insertContent(currentContent);
                ToastHelper.toast(R.string.blocked_tag_queued, blockedTagsLocal.get(0));
                setActionMode(ActionMode.VIEW_QUEUE);
            }
            return;
        }

        final String replacementTitleFinal = replacementTitle;
        // No reason to block or ignore -> actually add to the queue
        if (Preferences.getQueueNewDownloadPosition() == QUEUE_NEW_DOWNLOADS_POSITION_ASK && Preferences.getBrowserDlAction() == DL_ACTION_ASK) {
            AddQueueMenu.Companion.show(
                    this,
                    webView,
                    this,
                    (position1, item1) -> DownloadModeMenu.Companion.show(
                            this,
                            webView,
                            this,
                            (position2, item2) -> addToQueue(
                                    (0 == position1) ? ContentHelper.QueuePosition.TOP : ContentHelper.QueuePosition.BOTTOM,
                                    (0 == position2) ? Content.DownloadMode.DOWNLOAD : Content.DownloadMode.STREAM,
                                    isReplaceDuplicate,
                                    replacementTitleFinal
                            ), null
                    )
            );
        } else if (Preferences.getQueueNewDownloadPosition() == QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            AddQueueMenu.Companion.show(
                    this,
                    webView,
                    this,
                    (position, item) -> addToQueue(
                            (0 == position) ? ContentHelper.QueuePosition.TOP : ContentHelper.QueuePosition.BOTTOM,
                            Preferences.getBrowserDlAction(),
                            isReplaceDuplicate,
                            replacementTitleFinal
                    )
            );
        } else if (Preferences.getBrowserDlAction() == DL_ACTION_ASK) {
            DownloadModeMenu.Companion.show(
                    this,
                    webView,
                    this,
                    (position, item) -> addToQueue(
                            Preferences.getQueueNewDownloadPosition(),
                            (0 == position) ? Content.DownloadMode.DOWNLOAD : Content.DownloadMode.STREAM,
                            isReplaceDuplicate,
                            replacementTitleFinal
                    ), null
            );
        } else {
            addToQueue(Preferences.getQueueNewDownloadPosition(), Preferences.getBrowserDlAction(), isReplaceDuplicate, replacementTitleFinal);
        }
    }

    /**
     * Add current content to the downloads queue
     *
     * @param position           Target position in the queue (top or bottom)
     * @param downloadMode       Download mode for this content
     * @param isReplaceDuplicate True if existing duplicate book has to be replaced upon download completion
     */
    private void addToQueue(
            @ContentHelper.QueuePosition int position,
            @Content.DownloadMode int downloadMode,
            boolean isReplaceDuplicate,
            @Nullable String replacementTitle) {
        if (null == currentContent) return;
        Point coords = Helper.getCenter(binding.quickDlFeedback);
        if (coords != null && View.VISIBLE == binding.quickDlFeedback.getVisibility()) {
            Helper.setMargins(binding.animatedCheck,
                    coords.x - binding.animatedCheck.getWidth() / 2,
                    coords.y - binding.animatedCheck.getHeight() / 2, 0, 0);
        } else {
            Helper.setMargins(binding.animatedCheck,
                    (webView.getWidth() / 2) - (binding.animatedCheck.getWidth() / 2),
                    (webView.getHeight() / 2) - (binding.animatedCheck.getHeight() / 2), 0, 0);
        }
        binding.animatedCheck.setVisibility(View.VISIBLE);
        ((Animatable) binding.animatedCheck.getDrawable()).start();
        new Handler(getMainLooper()).postDelayed(() -> {
            if (binding != null) binding.animatedCheck.setVisibility(View.GONE);
        }, 1000);
        currentContent.setDownloadMode(downloadMode);
        dao.addContentToQueue(
                currentContent,
                null,
                null,
                position,
                (isReplaceDuplicate) ? duplicateId : -1,
                replacementTitle,
                ContentQueueManager.INSTANCE.isQueueActive(this)
        );
        if (Preferences.isQueueAutostart()) ContentQueueManager.INSTANCE.resumeQueue(this);
        setActionMode(ActionMode.VIEW_QUEUE);
    }

    /**
     * Take the user to the queue screen
     */
    private void goToQueue() {
        Intent intent = new Intent(this, QueueActivity.class);

        if (currentContent != null) {
            QueueActivityBundle builder = new QueueActivityBundle();
            builder.setContentHash(currentContent.uniqueHash());
            builder.setErrorsTab(currentContent.getStatus().equals(StatusContent.ERROR));
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
        if (onlineContent.getUrl().isEmpty()) return ContentStatus.UNDOWNLOADABLE;
        if (onlineContent.getStatus() != null && onlineContent.getStatus().equals(StatusContent.IGNORED))
            return ContentStatus.UNDOWNLOADABLE;
        currentContent = null;

        Timber.i("Content Site, URL : %s, %s", onlineContent.getSite().getCode(), onlineContent.getUrl());
        String searchUrl = getStartSite().hasCoverBasedPageUpdates() ? onlineContent.getCoverImageUrl() : "";
        // TODO manage DB calls concurrency to avoid getting read transaction conflicts
        Content contentDB = dao.selectContentBySourceAndUrl(onlineContent.getSite(), onlineContent.getUrl(), searchUrl);

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

                    Response onlineCover = HttpHelper.getOnlineResourceFast(
                            HttpHelper.fixUrl(onlineContent.getCoverImageUrl(), getStartUrl()),
                            requestHeadersList,
                            getStartSite().useMobileAgent(),
                            getStartSite().useHentoidAgent(),
                            getStartSite().useWebviewAgent()
                    );
                    ResponseBody coverBody = onlineCover.body();
                    if (coverBody != null) {
                        InputStream bodyStream = coverBody.byteStream();
                        Bitmap b = DuplicateHelper.INSTANCE.getCoverBitmapFromStream(bodyStream);
                        pHash = DuplicateHelper.INSTANCE.calcPhash(DuplicateHelper.INSTANCE.getHashEngine(), b);
                    }
                } catch (IOException e) {
                    Timber.w(e);
                }
                // Look for duplicates
                try {
                    ImmutablePair<Content, Float> duplicateResult = ContentHelper.findDuplicate(
                            this,
                            onlineContent,
                            Preferences.isDuplicateBrowserUseTitle(),
                            Preferences.isDuplicateBrowserUseArtist(),
                            Preferences.isDuplicateBrowserUseSameLanguage(),
                            Preferences.isDuplicateBrowserUseCover(),
                            pHash,
                            dao);
                    if (duplicateResult != null) {
                        duplicateId = duplicateResult.left.getId();
                        duplicateSimilarity = duplicateResult.right;
                        // Content ID of the duplicate candidate of the currently viewed Content
                        boolean duplicateSameSite = duplicateResult.left.getSite().equals(onlineContent.getSite());
                        // Same site and similar => enable download button by default, but look for extra pics just in case
                        if (duplicateSameSite && Preferences.isDownloadPlusDuplicateTry() && !quickDownload)
                            searchForExtraImages(duplicateResult.left, onlineContent);
                    }
                } catch (Exception e) {
                    Timber.w(e);
                }
            }

            if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB
                onlineContent.setStatus(StatusContent.SAVED);
                ContentHelper.addContent(this, dao, onlineContent);
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
        parseResponseDisposable.clear();
        if (processContentDisposable != null)
            processContentDisposable.dispose(); // Cancel whichever process was happening before
        if (Preferences.isBrowserMode()) return;

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
        if (binding != null) binding.quickDlFeedback.setVisibility(View.INVISIBLE);
        if (null == currentContent) return;
        switch (status) {
            case ContentStatus.UNDOWNLOADABLE:
                onResultFailed();
                break;
            case ContentStatus.UNKNOWN:
                if (quickDownload) {
                    if (duplicateId > -1 && Preferences.isDownloadDuplicateAsk())
                        DuplicateDialogFragment.invoke(this, duplicateId, currentContent.getQtyPages(), duplicateSimilarity, false);
                    else
                        processDownload(true, false, false);
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

    @Override
    public void onNoResult() {
        runOnUiThread(this::disableActions);
    }

    @Override
    public void onResultFailed() {
        runOnUiThread(() -> ToastHelper.toast(R.string.web_unparsable));
        parseResponseDisposable.clear();
    }

    private void searchForExtraImages(
            @NonNull final Content storedContent,
            @NonNull final Content onlineContent) {
        if (searchExtraImagesDisposable != null)
            searchExtraImagesDisposable.dispose(); // Cancel previous operation
        searchExtraImagesDisposable = Single.fromCallable(() -> doSearchForExtraImages(storedContent, onlineContent))
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

        // Attach chapters to stored images if they don't have any (old downloads made with versions of the app that didn't detect chapters)
        List<Chapter> storedChapters = storedContent.getChapters();
        if (!positionMap.isEmpty() && minOnlineImageOrder < maxStoredImageOrder && (null == storedChapters || storedChapters.isEmpty())) {
            List<ImageFile> storedImages = storedContent.getImageFiles();
            if (null == storedImages) storedImages = Collections.emptyList();
            for (ImageFile img : storedImages) {
                if (null == img.getLinkedChapter()) {
                    Chapter targetChapter = positionMap.get(img.getOrder());
                    if (targetChapter != null) img.setChapter(targetChapter);
                }
            }
            dao.insertImageFiles(storedImages);
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
        searchExtraImagesDisposable.dispose();
        if (binding != null) {
            binding.progressBar.setProgress(binding.progressBar.getMax());
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.progressBar.setProgressTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));
        }

        if (null == currentContent || additionalImages.isEmpty()) return;

        if (currentContent.getUrl().equalsIgnoreCase(onlineContent.getUrl()) || duplicateId == storedContent.getId()) { // User hasn't left the book page since
            // Retrieve the URLs of stored pages
            Set<String> storedUrls = new HashSet<>();
            if (storedContent.getImageFiles() != null) {
                storedUrls.addAll(Stream.of(storedContent.getImageFiles()).filter(i -> ContentHelper.isInLibrary(i.getStatus())).map(ImageFile::getUrl).toList());
            }
            // Memorize the title of the online content (to update title of stored book later)
            onlineContentTitle = onlineContent.getTitle();

            // Display the "download more" button only if extra images URLs aren't duplicates
            List<ImageFile> additionalNonDownloadedImages = Stream.of(additionalImages).filterNot(i -> storedUrls.contains(i.getUrl())).toList();
            if (!additionalNonDownloadedImages.isEmpty()) {
                extraImages = additionalNonDownloadedImages;
                setActionMode(ActionMode.DOWNLOAD_PLUS);
                if (binding != null) {
                    binding.actionBtnBadge.setText(String.format(Locale.ENGLISH, "%d", additionalNonDownloadedImages.size()));
                    binding.actionBtnBadge.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void updateDownloadedBooksUrls() {
        synchronized (downloadedBooksUrls) {
            downloadedBooksUrls.clear();
            downloadedBooksUrls.addAll(
                    Stream.of(dao.selectAllSourceUrls(getStartSite()))
                            .map(HttpHelper::simplifyUrl)
                            .filterNot(String::isEmpty)
                            .toList()
            );
        }
    }

    private void updateMergedBooksUrls() {
        synchronized (mergedBooksUrls) {
            mergedBooksUrls.clear();
            mergedBooksUrls.addAll(
                    Stream.of(dao.selectAllMergedUrls(getStartSite()))
                            .map(s -> s.replace(getStartSite().getUrl(), ""))
                            .map(s -> s.replaceAll("\\b|/galleries|/gallery|/g|/entry\\b", "")) //each sites "gallery" path
                            .map(HttpHelper::simplifyUrl)
                            .filterNot(String::isEmpty)
                            .toList()
            );
        }
    }

    private void updatePrefBlockedTags() {
        prefBlockedTags = Preferences.getBlockedTags();
    }

    private void clearPrefBlockedTags() {
        prefBlockedTags.clear();
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
            if (webClient.isMarkDownloaded()) updateDownloadedBooksUrls();
            if (event.content != null && event.content.equals(currentContent) && event.content.getStatus().equals(StatusContent.DOWNLOADED)) {
                setActionMode(ActionMode.READ);
            }
        }
    }

    void showTooltip(@StringRes int resource, boolean always) {
        TooltipHelper.showTooltip(this, resource, ArrowOrientation.BOTTOM, binding.bottomNavigation, this, always);
    }

    @Override
    public void onDownloadDuplicate(@DuplicateDialogFragment.ActionMode int actionMode) {
        processDownload(false, actionMode == DuplicateDialogFragment.ActionMode.DOWNLOAD_PLUS, actionMode == DuplicateDialogFragment.ActionMode.REPLACE);
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
     * Show the browser settings dialog
     */
    private void onSettingsClick() {
        Intent intent = new Intent(this, PrefsActivity.class);

        PrefsBundle prefsBundle = new PrefsBundle();
        prefsBundle.setBrowserPrefs(true);
        intent.putExtras(prefsBundle.getBundle());

        startActivity(intent);
    }

    /**
     * Show the About page
     */
    private void onAboutClick() {
        startActivity(new Intent(this, AboutActivity.class));
    }

    @Override
    public List<String> getAllSiteUrls() {
        return new ArrayList<>(downloadedBooksUrls); // Work on a copy to avoid any thread-synch issue
    }

    @Override
    public List<String> getAllMergedBooksUrls() {
        return new ArrayList<>(mergedBooksUrls);
    }

    @Override
    public List<String> getPrefBlockedTags() {
        return new ArrayList<>(prefBlockedTags);
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param prefs Shared preferences object
     * @param key   Key that has been changed
     */
    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        boolean reload = false;
        if (Preferences.Key.BROWSER_DL_ACTION.equals(key)) {
            downloadIcon = (Preferences.getBrowserDlAction() == Content.DownloadMode.STREAM) ? R.drawable.selector_download_stream_action : R.drawable.selector_download_action;
            setActionMode(actionButtonMode);
        } else if (Preferences.Key.BROWSER_MARK_DOWNLOADED.equals(key)) {
            customCss = null;
            webClient.setMarkDownloaded(Preferences.isBrowserMarkDownloaded());
            if (webClient.isMarkDownloaded()) updateDownloadedBooksUrls();
            reload = true;
        } else if (Preferences.Key.BROWSER_MARK_MERGED.equals(key)) {
            customCss = null;
            webClient.setMarkMerged(Preferences.isBrowserMarkMerged());
            if (webClient.isMarkMerged()) updateMergedBooksUrls();
            reload = true;
        } else if (Preferences.Key.BROWSER_MARK_BLOCKED.equals(key)) {
            customCss = null;
            webClient.setMarkBlockedTags(Preferences.isBrowserMarkBlockedTags());
            if (webClient.isMarkBlockedTags())
                updatePrefBlockedTags();
            else
                clearPrefBlockedTags();
            reload = true;
        } else if (Preferences.Key.DL_BLOCKED_TAGS.equals(key)) {
            updatePrefBlockedTags();
            reload = true;
        } else if (Preferences.Key.BROWSER_NHENTAI_INVISIBLE_BLACKLIST.equals(key)) {
            customCss = null;
            reload = true;
        } else if (Preferences.Key.BROWSER_DNS_OVER_HTTPS.equals(key)) {
            webClient.setDnsOverHttpsEnabled(Preferences.getDnsOverHttps() > -1);
            reload = true;
        } else if (Preferences.Key.BROWSER_QUICK_DL.equals(key)) {
            if (Preferences.isBrowserQuickDl())
                webView.setOnLongTapListener(this::onLongTap);
            else
                webView.setOnLongTapListener(null);
        } else if (Preferences.Key.BROWSER_QUICK_DL_THRESHOLD.equals(key)) {
            webView.setLongClickThreshold(Preferences.getBrowserQuickDlThreshold());
        }
        if (reload && !webClient.isLoading()) webView.reload();
    }

    private String getJsScript(String assetName) {
        StringBuilder sb = new StringBuilder();
        sb.append("javascript:");
        FileHelper.getAssetAsString(getAssets(), assetName, sb);
        return sb.toString();
    }

    public String getCustomCss() {
        if (null == customCss) {
            StringBuilder sb = new StringBuilder();
            if (Preferences.isBrowserMarkDownloaded() || Preferences.isBrowserMarkMerged() || Preferences.isBrowserMarkBlockedTags())
                FileHelper.getAssetAsString(getAssets(), "downloaded.css", sb);
            if (getStartSite().equals(Site.NHENTAI) && Preferences.isBrowserNhentaiInvisibleBlacklist())
                FileHelper.getAssetAsString(getAssets(), "nhentai_invisible_blacklist.css", sb);
            if (getStartSite().equals(Site.IMHENTAI))
                FileHelper.getAssetAsString(getAssets(), "imhentai.css", sb);
            if (getStartSite().equals(Site.PIXIV) && Preferences.isBrowserAugmented())
                FileHelper.getAssetAsString(getAssets(), "pixiv.css", sb);
            customCss = sb.toString();
        }
        return customCss;
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
        @SuppressWarnings("unused")
        public void onFetchCall(String url, String body) {
            Timber.d("fetch Begin %s : %s", url, body);
            handler.accept(url, body);
        }
    }

    // References :
    // https://medium.com/@madmuc/intercept-all-network-traffic-in-webkit-on-android-9c56c9262c85
    public static class XhrHandler {

        private final BiConsumer<String, String> handler;

        public XhrHandler(BiConsumer<String, String> handler) {
            this.handler = handler;
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void onXhrCall(String method, String url, String body) {
            Timber.d("XHR Begin %s : %s", url, body);
            handler.accept(url, body);
        }
    }
}
