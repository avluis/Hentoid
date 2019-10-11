package me.devsaki.hentoid.activities.sources;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.ResultListener;
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
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.views.ObservableWebView;
import okhttp3.Response;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
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

    protected static final int MODE_DL = 0;
    private static final int MODE_QUEUE = 1;
    private static final int MODE_READ = 2;

    // UI
    // Associated webview
    protected ObservableWebView webView;
    // Action buttons
    private FloatingActionButton fabAction;
    private FloatingActionButton fabRefreshOrStop;
    private FloatingActionButton fabHome;
    // Swipe layout
    private SwipeRefreshLayout swipeLayout;

    // Content currently viewed
    private Content currentContent;
    // Database
    private ObjectBoxDB db;
    // Indicated which mode the download FAB is in
    protected int fabActionMode;
    private boolean fabActionEnabled;

    private CustomWebViewClient webClient;
    private int chromeVersion;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_base_web);

        db = ObjectBoxDB.getInstance(this);

        if (getStartSite() == null) {
            Timber.w("Site is null!");
        } else {
            Timber.d("Loading site: %s", getStartSite());
        }

        fabAction = findViewById(R.id.fabAction);
        fabRefreshOrStop = findViewById(R.id.fabRefreshStop);
        fabHome = findViewById(R.id.fabHome);

        fabActionEnabled = false;

        initWebView();
        initSwipeLayout();

        String intentUrl = "";
        if (getIntent().getExtras() != null) {
            BaseWebActivityBundle.Parser parser = new BaseWebActivityBundle.Parser(getIntent().getExtras());
            intentUrl = parser.getUrl();
        }
        webView.loadUrl(0 == intentUrl.length() ? getStartSite().getUrl() : intentUrl);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
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
        HentoidApp.reset(this);
    }

    // Fix for a crash on 5.1.1
    // https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview
    // As fallback solution _only_ since it breaks other stuff in the webview (choice in SELECT tags for instance)
    public static Context getFixedContext(Context context) {
        return context.createConfigurationContext(new Configuration());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {

        try {
            webView = new ObservableWebView(this);
        } catch (Resources.NotFoundException e) {
            // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
            // Creating with the application Context fixes this, but is not generally recommended for view creation
            webView = new ObservableWebView(getFixedContext(this));
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
        webView.setOnScrollChangedCallback((deltaX, deltaY) -> {
            if (!webClient.isLoading()) {
                if (deltaY <= 0) {
                    fabRefreshOrStop.show();
                    fabHome.show();
                    if (fabActionEnabled) fabAction.show();
                } else {
                    fabRefreshOrStop.hide();
                    fabHome.hide();
                    fabAction.hide();
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

        Timber.i("Using agent %s", webView.getSettings().getUserAgentString());
        chromeVersion = getChromeVersion();

        WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        webSettings.setUserAgentString(Consts.USER_AGENT_NEUTRAL);

        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        SwipeRefreshLayout refreshLayout = findViewById(R.id.swipe_container);
        if (refreshLayout != null) refreshLayout.addView(webView, layoutParams);
    }

    private int getChromeVersion() {
        String chromeString = "Chrome/";
        String defaultUserAgent = webView.getSettings().getUserAgentString();
        if (defaultUserAgent.contains(chromeString)) {
            int chromeIndex = defaultUserAgent.indexOf(chromeString);
            int dotIndex = defaultUserAgent.indexOf('.', chromeIndex);
            String version = defaultUserAgent.substring(chromeIndex + chromeString.length(), dotIndex);
            return Integer.parseInt(version);
        } else return -1;
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

    public void onRefreshStopFabClick(View view) {
        if (webClient.isLoading()) {
            webView.stopLoading();
        } else {
            webView.reload();
        }
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

    /**
     * Listener for Home floating action button : go back to Library view
     *
     * @param view Calling view (part of the mandatory signature)
     */
    public void onHomeFabClick(View view) {
        goHome();
    }

    /**
     * Listener for Action floating action button : download content, view queue or read content
     *
     * @param view Calling view (part of the mandatory signature)
     */
    public void onActionFabClick(View view) {
        if (MODE_DL == fabActionMode) processDownload();
        else if (MODE_QUEUE == fabActionMode) goToQueue();
        else if (MODE_READ == fabActionMode && currentContent != null) {
            currentContent = db.selectContentBySourceAndUrl(currentContent.getSite(), currentContent.getUrl());
            if (currentContent != null) {
                if (StatusContent.DOWNLOADED == currentContent.getStatus()
                        || StatusContent.ERROR == currentContent.getStatus()
                        || StatusContent.MIGRATED == currentContent.getStatus()) {
                    ContentHelper.openContent(this, currentContent);
                } else {
                    fabAction.hide();
                }
            }
        }
    }

    private void changeFabActionMode(int mode) {
        @DrawableRes int resId = R.drawable.ic_menu_about;
        if (MODE_DL == mode) {
            resId = R.drawable.ic_action_download;
        } else if (MODE_QUEUE == mode) {
            resId = R.drawable.ic_action_queue;
        } else if (MODE_READ == mode) {
            resId = R.drawable.ic_action_play;
        }
        fabActionMode = mode;
        setFabIcon(fabAction, resId);
        fabActionEnabled = true;
// Timber.i(">> FAB SHOW");
        fabAction.show();
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     */
    void processDownload() {
        if (null == currentContent) return;

        if (currentContent.getId() > 0)
            currentContent = db.selectContentById(currentContent.getId());

        if (null == currentContent) return;

        if (StatusContent.DOWNLOADED == currentContent.getStatus()) {
            ToastUtil.toast(this, R.string.already_downloaded);
            changeFabActionMode(MODE_READ);
            return;
        }
        ToastUtil.toast(this, R.string.add_to_queue);

        currentContent.setStatus(StatusContent.DOWNLOADING);
        db.insertContent(currentContent);

        List<QueueRecord> queue = db.selectQueue();
        int lastIndex = 1;
        if (!queue.isEmpty()) {
            lastIndex = queue.get(queue.size() - 1).rank + 1;
        }
        db.insertQueue(currentContent.getId(), lastIndex);

        ContentQueueManager.getInstance().resumeQueue(this);

        changeFabActionMode(MODE_QUEUE);
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
    private void processContent(Content content) {
        if (null == content || null == content.getUrl()) {
            return;
        }

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
            changeFabActionMode(MODE_DL);
        }

        if (isInCollection) changeFabActionMode(MODE_READ);
        if (isInQueue) changeFabActionMode(MODE_QUEUE);

        currentContent = content;
    }

    public void onResultReady(Content results, long totalContent) {
        processContent(results);
    }

    public void onResultFailed(String message) {
        runOnUiThread(() -> ToastUtil.toast(HentoidApp.getAppContext(), R.string.web_unparsable));
    }

    /**
     * Analyze loaded HTML to display download button
     * Override blocked content with empty content
     */
    class CustomWebViewClient extends WebViewClient {

        protected final CompositeDisposable compositeDisposable = new CompositeDisposable();
        private final ByteArrayInputStream nothing = new ByteArrayInputStream("".getBytes());
        protected final ResultListener<Content> listener;
        private final Pattern filteredUrlPattern;
        private final HtmlAdapter<ContentParser> htmlAdapter;

        private String restrictedDomainName = "";
        private boolean isPageLoading = false;
        boolean isHtmlLoaded = false;


        @SuppressWarnings("unchecked")
        CustomWebViewClient(String filteredUrl, ResultListener<Content> listener) {
            this.listener = listener;

            Class c = ContentParserFactory.getInstance().getContentParserClass(getStartSite());
            final Jspoon jspoon = Jspoon.create();
            htmlAdapter = jspoon.adapter(c); // Unchecked but alright

            if (filteredUrl.length() > 0) filteredUrlPattern = Pattern.compile(filteredUrl);
            else filteredUrlPattern = null;
        }

        void destroy() {
            Timber.d("WebClient destroyed");
            compositeDisposable.clear();
        }

        private void hideActionFab() {
// Timber.i(">> FAB HIDE");
            fabAction.hide();
            fabActionEnabled = false;
        }

        void restrictTo(String s) {
            restrictedDomainName = s;
        }

        private boolean isPageFiltered(String url) {
            if (null == filteredUrlPattern) return false;

            Matcher matcher = filteredUrlPattern.matcher(url);
            return matcher.find();
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
         * - Does not work on on 4.4 & 4.4.2 because calling CookieManager.getCookie inside shouldInterceptRequest triggers a deadlock
         * https://issuetracker.google.com/issues/36989494
         * - Does not work on Chrome 58-71 because sameSite cookies are not published by CookieManager.getCookie (causes issues on nHentai)
         * https://bugs.chromium.org/p/chromium/issues/detail?id=780491
         *
         * @return true if HTML content can be served by a single OkHttp request,
         * false if the webview has to handle the display (OkHttp will be used as a 2nd request for parsing)
         */
        private boolean useSingleOkHttpRequest() {
            return (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH
                    && (chromeVersion < 58 || chromeVersion > 71)
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
            setFabIcon(fabRefreshOrStop, R.drawable.ic_action_clear);
            fabRefreshOrStop.show();
            fabHome.show();
            isPageLoading = true;
// Timber.i(">> onPageStarted %s", url);
            if (!isHtmlLoaded) hideActionFab();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            isPageLoading = false;
            isHtmlLoaded = false; // Reset for the next page
            setFabIcon(fabRefreshOrStop, R.drawable.ic_action_refresh);
// Timber.i(">> onPageFinished %s", url);
        }

        @Override
        @Deprecated
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull String url) {
            // Prevents processing the page twice on Lollipop and above
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                WebResourceResponse result = shouldInterceptRequestInternal(view, url, null);
                if (result != null) return result;
            }
            return super.shouldInterceptRequest(view, url);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                          @NonNull WebResourceRequest request) {
            String url = request.getUrl().toString();
            WebResourceResponse result = shouldInterceptRequestInternal(view, url, request.getRequestHeaders());
            if (result != null) return result;
            else return super.shouldInterceptRequest(view, request);
        }

        @Nullable
        private WebResourceResponse shouldInterceptRequestInternal(@NonNull WebView view,
                                                                   @NonNull String url,
                                                                   @Nullable Map<String, String> headers) {
            if (isUrlForbidden(url)) {
                return new WebResourceResponse("text/plain", "utf-8", nothing);
            } else {
// Timber.i(">> SIR 1 %s %s", isPageLoading, url);
                if (/*!isPageLoading &&*/ isPageFiltered(url)) return parseResponse(url, headers);
// Timber.i(">> SIR 2 %s %s", isPageLoading, url);
                return null;
            }
        }

        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> headers) {
// Timber.i(">> parseResponse %s", urlStr);
            List<Pair<String, String>> headersList = new ArrayList<>();

            if (headers != null)
                for (String key : headers.keySet())
                    headersList.add(new Pair<>(key, headers.get(key)));

            if (useSingleOkHttpRequest()) {
                String cookie = CookieManager.getInstance().getCookie(urlStr);
                if (cookie != null)
                    headersList.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookie));
            }

            try {
                Response response = HttpHelper.getOnlineResource(urlStr, headersList, getStartSite().canKnowHentoidAgent());
                if (null == response.body()) throw new IOException("Empty body");

                InputStream parserStream;
                WebResourceResponse result;
                if (useSingleOkHttpRequest()) {
                    // Response body bytestream needs to be duplicated
                    // because Jsoup closes it, which makes it unavailable for the WebView to use
                    List<InputStream> is = Helper.duplicateInputStream(response.body().byteStream(), 2);
                    parserStream = is.get(0);
                    result = HttpHelper.okHttpResponseToWebResourceResponse(response, is.get(1));
                } else {
                    parserStream = response.body().byteStream();
                    result = null; // Default webview behaviour
                }

                compositeDisposable.add(
                        Single.fromCallable(() -> htmlAdapter.fromInputStream(parserStream, new URL(urlStr)).toContent(urlStr))
                                .subscribeOn(Schedulers.computation())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        content -> processContent(content, headersList),
                                        throwable -> {
                                            Timber.e(throwable, "Error parsing content.");
                                            isHtmlLoaded = true;
                                            listener.onResultFailed("");
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

        private void processContent(@Nonnull Content content, @Nonnull List<Pair<String, String>> headersList) {
// Timber.i(">> processContent 1");
            if (content.getStatus() != null && content.getStatus().equals(StatusContent.IGNORED))
                return;
// Timber.i(">> processContent 2");

            // Save cookies for future calls during download
            Map<String, String> params = new HashMap<>();
            for (Pair<String, String> p : headersList)
                if (p.first.equals(HttpHelper.HEADER_COOKIE_KEY))
                    params.put(HttpHelper.HEADER_COOKIE_KEY, p.second);

            content.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
            isHtmlLoaded = true;
            listener.onResultReady(content, 1);
        }

        /**
         * Indicated whether the current webpage is still loading or not
         *
         * @return True if current webpage is being loaded; false if not
         */
        public boolean isLoading() {
            return isPageLoading;
        }
    }

    // Workaround for https://issuetracker.google.com/issues/111316656
    private void setFabIcon(@Nonnull FloatingActionButton btn, @DrawableRes int resId) {
        btn.setImageResource(resId);
        btn.setImageMatrix(new Matrix());
    }
}
