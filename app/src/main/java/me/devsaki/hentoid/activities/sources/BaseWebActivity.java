package me.devsaki.hentoid.activities.sources;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Build;
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
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.BiFunction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.skydoves.balloon.ArrowOrientation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.threeten.bp.Instant;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.BaseActivity;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AlertStatus;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.fragments.BookmarksDialogFragment;
import me.devsaki.hentoid.json.UpdateInfo;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.views.NestedScrollWebView;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
import timber.log.Timber;

import static me.devsaki.hentoid.util.PermissionUtil.RQST_STORAGE_PERMISSION;
import static me.devsaki.hentoid.util.network.HttpHelper.HEADER_CONTENT_TYPE;
import static me.devsaki.hentoid.util.network.HttpHelper.getExtensionFromUri;

/**
 * Browser activity which allows the user to navigate a supported source.
 * No particular source should be filtered/defined here.
 * The source itself should contain every method it needs to function.
 */
public abstract class BaseWebActivity extends BaseActivity implements WebContentListener, BookmarksDialogFragment.Parent {

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


    // === UI
    // Associated webview
    protected NestedScrollWebView webView;
    // Bottom toolbar
    private BottomNavigationView bottomToolbar;
    // Bottom toolbar buttons
    private MenuItem backMenu;
    private MenuItem forwardMenu;
    private MenuItem seekMenu;
    private MenuItem refreshStopMenu;
    private MenuItem actionMenu;
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

    // === VARIABLES
    private CustomWebViewClient webClient;
    // Currently viewed content
    private Content currentContent = null;
    // Database
    private CollectionDAO objectBoxDAO;
    // Indicates which mode the download button is in
    protected @ActionMode
    int actionButtonMode;
    // Indicates which mode the seek button is in
    protected @SeekMode
    int seekButtonMode;
    // Alert to be displayed
    private UpdateInfo.SourceAlert alert;
    // Disposable to be used for punctual search
    private Disposable disposable;

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
        universalBlockedContent.add("google-analytics.com");
        universalBlockedContent.add("mc.yandex.ru");
        universalBlockedContent.add("mc.webvisor.org");
        universalBlockedContent.add("scorecardresearch.com");
        universalBlockedContent.add("hadskiz.com");
        universalBlockedContent.add("pushnotifications.click");
        universalBlockedContent.add("fingahvf.top");
        universalBlockedContent.add("displayvertising.com");
        universalBlockedContent.add("tsyndicate.com");
        universalBlockedContent.add("semireproji.pro");
        universalBlockedContent.add("defutohi.pro");
        universalBlockedContent.add("realsrv.com");
        universalBlockedContent.add("smartclick.net");
        universalBlockedContent.add("ulukaris.com");
        universalBlockedContent.add("alliedthirteen.com");
        universalBlockedContent.add("acknowledgenightsabstain.com");
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

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        objectBoxDAO = new ObjectBoxDAO(this);

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
        refreshStopMenu = toolbar.getMenu().findItem(R.id.web_menu_refresh_stop);

        bottomToolbar = findViewById(R.id.bottom_navigation);
        bottomToolbar.setOnNavigationItemSelectedListener(this::onMenuItemSelected);
        bottomToolbar.setItemIconTintList(null); // Hack to make selector resource work
        backMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_back);
        forwardMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_forward);
        seekMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_seek);
        actionMenu = bottomToolbar.getMenu().findItem(R.id.web_menu_download);

        // Webview
        animatedCheck = findViewById(R.id.animated_check);
        initWebView();
        initSwipeLayout();
        webView.loadUrl(getStartUrl());

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        // Alert banners
        topAlertBanner = findViewById(R.id.top_alert);
        topAlertIcon = findViewById(R.id.top_alert_icon);
        topAlertMessage = findViewById(R.id.top_alert_txt);

        bottomAlertBanner = findViewById(R.id.bottom_alert);
        bottomAlertMessage = findViewById(R.id.bottom_alert_txt);

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
            case R.id.web_menu_download:
                this.onActionClick();
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
        if (currentContent != null && url != null && getWebClient().isGalleryPage(url))
            processContent(currentContent, false);
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

        if (objectBoxDAO != null) objectBoxDAO.cleanup();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    // Validate permissions
    // TODO find something better than that
    private void checkPermissions() {
        if (!PermissionUtil.requestExternalStorageReadWritePermission(this, RQST_STORAGE_PERMISSION))
            ToastUtil.toast("Storage permission denied - cannot use the downloader");
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
            webView.setInitialScale(Preferences.Default.WEBVIEW_INITIAL_ZOOM_DEFAULT);
            webView.getSettings().setLoadWithOverviewMode(true);
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);


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

        CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        SwipeRefreshLayout refreshLayout = findViewById(R.id.swipe_container);
        if (refreshLayout != null) refreshLayout.addView(webView, layoutParams);
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
        BookmarksDialogFragment.invoke(this, getStartSite(), Helper.protect(webView.getTitle()), Helper.protect(webView.getUrl()));
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
        if (Helper.copyPlainTextToClipboard(this, Helper.protect(webView.getUrl())))
            ToastUtil.toast(R.string.web_url_clipboard);
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
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    public void openUrl(@NonNull final String url) {
        webView.loadUrl(url);
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
    public void onActionClick() {
        if (ActionMode.DOWNLOAD == actionButtonMode) processDownload(false, false);
        else if (ActionMode.DOWNLOAD_PLUS == actionButtonMode) processDownload(false, true);
        else if (ActionMode.VIEW_QUEUE == actionButtonMode) goToQueue();
        else if (ActionMode.READ == actionButtonMode && currentContent != null) {
            currentContent = objectBoxDAO.selectContentBySourceAndUrl(currentContent.getSite(), currentContent.getUrl());
            if (currentContent != null && (StatusContent.DOWNLOADED == currentContent.getStatus()
                    || StatusContent.ERROR == currentContent.getStatus()
                    || StatusContent.MIGRATED == currentContent.getStatus()))
                ContentHelper.openHentoidViewer(this, currentContent, null);
            else actionMenu.setEnabled(false);
        }
    }

    /**
     * Switch the action button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private void changeActionMode(@ActionMode int mode) {
        @DrawableRes int resId = R.drawable.ic_info;
        if (ActionMode.DOWNLOAD == mode) {
            resId = R.drawable.selector_download_action;
        } else if (ActionMode.DOWNLOAD_PLUS == mode) {
            resId = R.drawable.ic_action_download_plus;
        } else if (ActionMode.VIEW_QUEUE == mode) {
            resId = R.drawable.ic_action_queue;
        } else if (ActionMode.READ == mode) {
            resId = R.drawable.ic_action_play;
        }
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
            ToastUtil.toast(R.string.already_downloaded);
            if (!quickDownload) changeActionMode(ActionMode.READ);
            return;
        }

        if (isDownloadPlus) {
            currentContent.setStatus(StatusContent.SAVED);
            objectBoxDAO.insertContent(currentContent);
        }

        // Check if the tag blocker applies here
        List<String> blockedTags = ContentHelper.getBlockedTags(currentContent);
        if (!blockedTags.isEmpty()) {
            if (Preferences.getTagBlockingBehaviour() == Preferences.Constant.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE) { // Stop right here
                ToastUtil.toast(getResources().getString(R.string.blocked_tag, blockedTags.get(0)));
            } else { // Insert directly as an error
                List<ErrorRecord> errors = new ArrayList<>();
                errors.add(new ErrorRecord(ErrorType.BLOCKED, currentContent.getUrl(), "tags", "blocked tags : " + TextUtils.join(", ", blockedTags), Instant.now()));
                currentContent.setErrorLog(errors);
                currentContent.setStatus(StatusContent.ERROR);
                objectBoxDAO.insertContent(currentContent);
                ToastUtil.toast(getResources().getString(R.string.blocked_tag_queued, blockedTags.get(0)));
                changeActionMode(ActionMode.VIEW_QUEUE);
            }
            return;
        }

        animatedCheck.setVisibility(View.VISIBLE);
        ((Animatable) animatedCheck.getDrawable()).start();
        new Handler(getMainLooper()).postDelayed(() -> animatedCheck.setVisibility(View.GONE), 1000);
        objectBoxDAO.addContentToQueue(currentContent, null);
        if (Preferences.isQueueAutostart()) ContentQueueManager.getInstance().resumeQueue(this);
        changeActionMode(ActionMode.VIEW_QUEUE);
    }

    /**
     * Take the user to the queue screen
     */
    private void goToQueue() {
        Intent intent = new Intent(this, QueueActivity.class);

        QueueActivityBundle.Builder builder = new QueueActivityBundle.Builder();
        builder.setContentHash(currentContent.hashCode());
        builder.setIsErrorsTab(currentContent.getStatus().equals(StatusContent.ERROR));
        intent.putExtras(builder.getBundle());

        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            WebBackForwardList webBFL = webView.copyBackForwardList();
            String originalUrl = Helper.protect(webView.getOriginalUrl());
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
     * @param content       Currently displayed content
     * @param quickDownload True if the action has been triggered by a quick download
     *                      (which means we're not on a book gallery page but on the book list page)
     * @return The status of the Content after being processed
     */
    private @ContentStatus
    int processContent(@NonNull Content content, boolean quickDownload) {
        @ContentStatus int result = ContentStatus.UNKNOWN;
        if (content.getUrl().isEmpty()) return result;

        Timber.i("Content Site, URL : %s, %s", content.getSite().getCode(), content.getUrl());
        Content contentDB = objectBoxDAO.selectContentBySourceAndUrl(content.getSite(), content.getUrl());

        boolean isInCollection = (contentDB != null && ContentHelper.isInLibrary(contentDB.getStatus()));
        boolean isInQueue = (contentDB != null && ContentHelper.isInQueue(contentDB.getStatus()));

        if (!isInCollection && !isInQueue) {
            if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB
                content.setStatus(StatusContent.SAVED);
                ContentHelper.addContent(this, objectBoxDAO, content);
            } else {
                content = contentDB;
            }
            if (!quickDownload) changeActionMode(ActionMode.DOWNLOAD);
        } else {
            content.setId(contentDB.getId());
            content.setStatus(contentDB.getStatus());
        }

        if (isInCollection) {
            if (!quickDownload) changeActionMode(ActionMode.READ);
            result = ContentStatus.IN_COLLECTION;
            searchForMoreImages(contentDB); // Async; might switch READ to DOWNLOAD_PLUS a couple seconds later
        }
        if (isInQueue) {
            if (!quickDownload) changeActionMode(ActionMode.VIEW_QUEUE);
            result = ContentStatus.IN_QUEUE;
        }

        if (webClient != null)
            webClient.setBlockedTags(ContentHelper.getBlockedTags(content));

        currentContent = content;
        return result;
    }

    public void onResultReady(@NonNull Content results, boolean quickDownload) {
        @ContentStatus int status = processContent(results, quickDownload);
        if (quickDownload) {
            if (ContentStatus.UNKNOWN == status) processDownload(true, false);
            else if (ContentStatus.IN_COLLECTION == status)
                ToastUtil.toast(R.string.already_downloaded);
            else if (ContentStatus.IN_QUEUE == status) ToastUtil.toast(R.string.already_queued);
        }
    }

    public void onResultFailed() {
        runOnUiThread(() -> ToastUtil.toast(R.string.web_unparsable));
    }

    private void searchForMoreImages(@NonNull final Content c) {
        disposable = Single.fromCallable(() -> doSearchForMoreImages(c))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(l -> onSearchForMoreImagesSuccess(c, l), Timber::e);
    }

    private List<ImageFile> doSearchForMoreImages(@NonNull final Content c) {
        List<ImageFile> result = Collections.emptyList();
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(c);
        try {
            List<ImageFile> imgs = parser.parseImageList(c);
            if (imgs.isEmpty()) return result;

            int coverCount = (imgs.get(0).isCover()) ? 1 : 0;
            Optional<Integer> maxImageOrder;
            if (c.getImageFiles() != null)
                maxImageOrder = Stream.of(c.getImageFiles()).filter(i -> i.getStatus().equals(StatusContent.DOWNLOADED)).map(ImageFile::getOrder).max(Integer::compareTo);
            else
                maxImageOrder = Optional.of(0);

            if (maxImageOrder.isPresent() && imgs.size() - coverCount > maxImageOrder.get())
                return Stream.of(imgs).filter(i -> i.getOrder() > maxImageOrder.get()).toList();
        } catch (Exception e) {
            Timber.w(e);
        }
        return result;
    }

    private void onSearchForMoreImagesSuccess(@NonNull final Content c, @NonNull final List<ImageFile> additionalImages) {
        disposable.dispose();
        if (additionalImages.isEmpty()) return;

        if (currentContent.equals(c)) { // User hasn't left the book page since
            // Append additional pages to the current book's list of pages
            List<ImageFile> updatedImgs = new ArrayList<>();
            if (c.getImageFiles() != null) updatedImgs.addAll(c.getImageFiles());

            updatedImgs.addAll(additionalImages);
            c.setImageFiles(updatedImgs);
            // Save it
            objectBoxDAO.insertContent(c);
            // Display the "download more" button
            changeActionMode(ActionMode.DOWNLOAD_PLUS);
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
        if (event.eventType == DownloadEvent.EV_COMPLETE && event.content != null && event.content.equals(currentContent) && event.content.getStatus().equals(StatusContent.DOWNLOADED)) {
            changeActionMode(ActionMode.READ);
        }
    }

    void showTooltip(@StringRes int resource, boolean always) {
        TooltipUtil.showTooltip(this, resource, ArrowOrientation.BOTTOM, bottomToolbar, this, always);
    }

    /**
     * Indicates if the given URL is forbidden by the current content filters
     *
     * @param url URL to be examinated
     * @return True if URL is forbidden according to current filters; false if not
     */
    protected boolean isUrlForbidden(@NonNull String url) {
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
     * Analyze loaded HTML to display download button
     * Override blocked content with empty content
     */
    class CustomWebViewClient extends WebViewClient {

        // Pre-built object to represent an empty input stream
        // (will be used instead of the actual stream when the requested resource is blocked)
        private final ByteArrayInputStream NOTHING = new ByteArrayInputStream("".getBytes());

        // Used to clear RxJava observers (avoiding memory leaks)
        protected final CompositeDisposable compositeDisposable = new CompositeDisposable();
        // Listener to the results of the page parser
        protected final WebContentListener listener;
        // List of the URL patterns identifying a parsable book gallery page
        private final List<Pattern> galleryUrlPattern = new ArrayList<>();
        // List of the URL patterns identifying a parsable book gallery page
        private final List<Pattern> resultsUrlPattern = new ArrayList<>();
        // Results URL rewriter to insert page to seek to
        private BiFunction<Uri, Integer, String> resultsUrlRewriter = null;
        // Adapter used to parse the HTML code of book gallery pages
        private final HtmlAdapter<? extends ContentParser> htmlAdapter;
        // Domain name for which link navigation is restricted
        private final List<String> restrictedDomainNames = new ArrayList<>();
        // Loading state of the current webpage (used for the refresh/stop feature)
        private boolean isPageLoading = false;
        // Loading state of the HTML code of the current webpage (used to trigger the action button)
        boolean isHtmlLoaded = false;
        // TODO doc
        List<String> blockedTags = Collections.emptyList();


        CustomWebViewClient(String[] galleryUrl, WebContentListener listener) {
            this.listener = listener;

            Class<? extends ContentParser> c = ContentParserFactory.getInstance().getContentParserClass(getStartSite());
            final Jspoon jspoon = Jspoon.create();
            htmlAdapter = jspoon.adapter(c); // Unchecked but alright

            for (String s : galleryUrl) galleryUrlPattern.add(Pattern.compile(s));
        }

        void destroy() {
            Timber.d("WebClient destroyed");
            compositeDisposable.clear();
        }

        void setResultsUrlPatterns(String... patterns) {
            for (String s : patterns) resultsUrlPattern.add(Pattern.compile(s));
        }

        void setResultUrlRewriter(@NonNull BiFunction<Uri, Integer, String> rewriter) {
            resultsUrlRewriter = rewriter;
        }

        void setBlockedTags(@NonNull final List<String> blockedTags) {
            this.blockedTags = blockedTags;
        }

        /**
         * Restrict link navigation to a given domain name
         *
         * @param s Domain name to restrict link navigation to
         */
        protected void restrictTo(String s) {
            restrictedDomainNames.add(s);
        }

        void restrictTo(String... s) {
            restrictedDomainNames.addAll(Arrays.asList(s));
        }

        private boolean isHostNotInRestrictedDomains(@NonNull String host) {
            if (restrictedDomainNames.isEmpty()) return false;

            for (String s : restrictedDomainNames) {
                if (host.contains(s)) return false;
            }

            Timber.i("Unrestricted host detected : %s", host);
            return true;
        }

        /**
         * Indicates if the given URL is a book gallery page
         *
         * @param url URL to test
         * @return True if the given URL represents a book gallery page
         */
        boolean isGalleryPage(@NonNull final String url) {
            if (galleryUrlPattern.isEmpty()) return false;

            for (Pattern p : galleryUrlPattern) {
                Matcher matcher = p.matcher(url);
                if (matcher.find()) return true;
            }
            return false;
        }

        /**
         * Indicates if the given URL is a results page
         *
         * @param url URL to test
         * @return True if the given URL represents a results page
         */
        boolean isResultsPage(@NonNull final String url) {
            if (resultsUrlPattern.isEmpty()) return false;

            for (Pattern p : resultsUrlPattern) {
                Matcher matcher = p.matcher(url);
                if (matcher.find()) return true;
            }
            return false;
        }

        /**
         * Rewrite the given URL to seek the given page number
         *
         * @param url     URL to be rewritten
         * @param pageNum page number to seek
         * @return Given URL to be rewritten
         */
        protected String seekResultsUrl(@NonNull String url, int pageNum) {
            if (null == resultsUrlRewriter || !isResultsPage(url) || isGalleryPage(url)) return url;
            else return resultsUrlRewriter.apply(Uri.parse(url), pageNum);
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
                    && (HttpHelper.getChromeVersion() < 45 || HttpHelper.getChromeVersion() > 71)
            );
        }

        /**
         * @deprecated kept for API19-API23
         */
        @Override
        @Deprecated
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return shouldOverrideUrlLoadingInternal(view, url, null);
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldOverrideUrlLoadingInternal(view, request.getUrl().toString(), request.getRequestHeaders());
        }

        protected boolean shouldOverrideUrlLoadingInternal(
                @NonNull final WebView view,
                @NonNull final String url,
                @Nullable final Map<String, String> requestHeaders) {
            if (isUrlForbidden(url) || !url.startsWith("http")) return true;

            // Download and open the torrent file
            // NB : Opening the URL itself won't work when the tracker is private
            // as the 3rd party torrent app doesn't have access to it
            if (HttpHelper.getExtensionFromUri(url).equals("torrent")) {
                disposable = Single.fromCallable(() -> downloadFile(view.getContext(), url, requestHeaders))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(uri -> {
                            disposable.dispose();
                            FileHelper.openFile(view.getContext(), uri);
                        }, e -> {
                            disposable.dispose();
                            ToastUtil.toast("Downloading torrent failed : " + e.getMessage());
                            Timber.w(e);
                        });
            }

            String host = Uri.parse(url).getHost();
            return host != null && isHostNotInRestrictedDomains(host);
        }

        /**
         * Download the resource at the given URL to the app's cache folder
         *
         * @param context        Context to be used
         * @param url            URL to load
         * @param requestHeaders Request headers (optional)
         * @return Saved file, if successful
         * @throws IOException if anything horrible happens during the download
         */
        private File downloadFile(@NonNull final Context context,
                                  @NonNull final String url,
                                  @Nullable final Map<String, String> requestHeaders) throws IOException {
            List<Pair<String, String>> requestHeadersList;
            requestHeadersList = HttpHelper.webResourceHeadersToOkHttpHeaders(requestHeaders, url);

            Response onlineFileResponse = HttpHelper.getOnlineResource(url, requestHeadersList, getStartSite().useMobileAgent(), getStartSite().useHentoidAgent());
            ResponseBody body = onlineFileResponse.body();
            if (null == body)
                throw new IOException("Empty response from server");

            File cacheDir = context.getCacheDir();
            // Using a random file name rather than the original name to avoid errors caused by path length
            File file = new File(cacheDir.getAbsolutePath() + File.separator + new Random().nextInt(10000) + "." + getExtensionFromUri(url));
            if (!file.createNewFile())
                throw new IOException("Could not create file " + file.getPath());

            Uri torrentFileUri = Uri.fromFile(file);
            FileHelper.saveBinary(context, torrentFileUri, body.bytes());
            return file;
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
            // Display download button tooltip if a book page has been reached
            if (isGalleryPage(url)) showTooltip(R.string.help_web_download, false);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            isPageLoading = false;
            isHtmlLoaded = false; // Reset for the next page
            refreshStopMenu.setIcon(R.drawable.ic_action_refresh);
            refreshNavigationMenu();
        }

        /**
         * Refresh the visuals of the buttons of the navigation menu
         */
        private void refreshNavigationMenu() {
            backMenu.setEnabled(webView.canGoBack());
            forwardMenu.setEnabled(webView.canGoForward());
            boolean isResults = isResultsPage(Helper.protect(webView.getUrl()));
            changeSeekMode(isResults ? SeekMode.PAGE : SeekMode.GALLERY, isResults || backListContainsGallery(webView.copyBackForwardList()) > -1);
            // Manager bottom alert banner visibility
            if (isGalleryPage(webView.getUrl())) displayBottomAlertBanner(blockedTags);
            else onBottomAlertCloseClick(null);
        }

        /**
         * Note : this method is called by a non-UI thread
         */
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Data fetched with POST is out of scope of analysis and adblock
            if (!request.getMethod().equalsIgnoreCase("get")) {
                Timber.v("[%s] ignored by interceptor; method = %s", url, request.getMethod());
                return super.shouldInterceptRequest(view, request);
            }

            WebResourceResponse result = shouldInterceptRequestInternal(url, request.getRequestHeaders());
            if (result != null) return result;
            else return super.shouldInterceptRequest(view, request);
        }

        /**
         * Determines if the page at the given URL is to be processed
         *
         * @param url     Called URL
         * @param headers Request headers
         * @return Processed response if the page has been processed;
         * null if vanilla processing should happen instead
         */
        @Nullable
        private WebResourceResponse shouldInterceptRequestInternal(@NonNull final String url,
                                                                   @Nullable final Map<String, String> headers) {
            if (isUrlForbidden(url) || !url.startsWith("http")) {
                return new WebResourceResponse("text/plain", "utf-8", NOTHING);
            } else {
                if (isGalleryPage(url)) return parseResponse(url, headers, true, false);

                // If we're here to remove "dirty elements", we only do it on HTML resources (URLs without extension)
                if (dirtyElements != null && HttpHelper.getExtensionFromUri(url).isEmpty())
                    return parseResponse(url, headers, false, false);

                return null;
            }
        }

        /**
         * Process the given webpage in a background thread (used by quick download)
         *
         * @param urlStr URL of the page to parse
         */
        void parseResponseAsync(@NonNull String urlStr) {
            compositeDisposable.add(
                    Completable.fromCallable(() -> parseResponse(urlStr, null, true, true))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> {
                            }, Timber::e)
            );
        }

        /**
         * Process the webpage at the given URL
         *
         * @param urlStr             URL of the page to process
         * @param requestHeaders     Request headers to use
         * @param analyzeForDownload True if the page has to be analyzed for potential downloads;
         *                           false if only ad removal should happen
         * @param quickDownload      True if the present call has been triggered by a quick download action
         * @return Processed response if the page has been actually processed;
         * null if vanilla processing should happen instead
         */
        @SuppressLint("NewApi")
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            Helper.assertNonUiThread();
            // If we're here for dirty content removal only, and can't use the OKHTTP request, it's no use going further
            if (!analyzeForDownload && !canUseSingleOkHttpRequest()) return null;

            blockedTags = Collections.emptyList();
            List<Pair<String, String>> requestHeadersList = HttpHelper.webResourceHeadersToOkHttpHeaders(requestHeaders, urlStr);

            try {
                // Query resource here, using OkHttp
                Response response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, getStartSite().useMobileAgent(), getStartSite().useHentoidAgent());

                // Scram if the response is a redirection or an error
                if (response.code() >= 300) return null;

                // Scram if the response is something else than html
                Pair<String, String> contentType = HttpHelper.cleanContentType(response.header(HEADER_CONTENT_TYPE, ""));
                if (!contentType.first.isEmpty() && !contentType.first.equals("text/html"))
                    return null;

                // Scram if the response is empty
                ResponseBody body = response.body();
                if (null == body) throw new IOException("Empty body");

                InputStream parserStream;
                WebResourceResponse result;
                if (canUseSingleOkHttpRequest()) {
                    InputStream browserStream;
                    if (analyzeForDownload) {
                        // Response body bytestream needs to be duplicated
                        // because Jsoup closes it, which makes it unavailable for the WebView to use
                        List<InputStream> is = Helper.duplicateInputStream(body.byteStream(), 2);
                        parserStream = is.get(0);
                        browserStream = is.get(1);
                    } else {
                        parserStream = null;
                        browserStream = body.byteStream();
                    }

                    // Remove dirty elements from HTML resources
                    if (dirtyElements != null) {
                        browserStream = removeCssElementsFromStream(browserStream, urlStr, dirtyElements);
                        if (null == browserStream) return null;
                    }

                    // Convert OkHttp response to the expected format
                    result = HttpHelper.okHttpResponseToWebResourceResponse(response, browserStream);

                    // Manually set cookie if present in response header (has to be set manually because we're using OkHttp right now, not the webview)
                    if (result.getResponseHeaders().containsKey("set-cookie") || result.getResponseHeaders().containsKey("Set-Cookie")) {
                        String cookiesStr = result.getResponseHeaders().get("set-cookie");
                        if (null == cookiesStr)
                            cookiesStr = result.getResponseHeaders().get("Set-Cookie");
                        if (cookiesStr != null) HttpHelper.setCookies(urlStr, cookiesStr);
                    }
                } else {
                    parserStream = body.byteStream();
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

        /**
         * Process Content parsed from a webpage
         *
         * @param content        Content to be processed
         * @param requestHeaders HTTP headers of the request that has generated the Content
         * @param quickDownload  True if the present call has been triggered by a quick download action
         */
        private void processContent(@Nonnull Content content, @Nonnull List<Pair<String, String>> requestHeaders, boolean quickDownload) {
            if (content.getStatus() != null && content.getStatus().equals(StatusContent.IGNORED))
                return;

            // Save cookies for future calls during download
            Map<String, String> params = new HashMap<>();
            for (Pair<String, String> p : requestHeaders)
                if (p.first.equals(HttpHelper.HEADER_COOKIE_KEY))
                    params.put(HttpHelper.HEADER_COOKIE_KEY, p.second);

            content.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
            isHtmlLoaded = true;

            listener.onResultReady(content, quickDownload);
        }

        /**
         * Indicate whether the current webpage is still loading or not
         *
         * @return True if current webpage is being loaded; false if not
         */
        boolean isLoading() {
            return isPageLoading;
        }

        /**
         * Remove nodes from the HTML document contained in the given stream, using a list of CSS selectors to identify them
         *
         * @param stream        Stream containing the HTML document to process
         * @param baseUri       Base URI if the document
         * @param dirtyElements CSS selectors of the nodes to remove
         * @return Stream containing the HTML document stripped from the elements to remove
         */
        @Nullable
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
                return null;
            }
        }

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
}
