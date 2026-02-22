package me.devsaki.hentoid.activities.sources

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.ColorStateList
import android.content.res.Resources.NotFoundException
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.MissingWebViewActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.bundles.BaseBrowserActivityBundle
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle
import me.devsaki.hentoid.activities.bundles.SettingsBundle
import me.devsaki.hentoid.activities.settings.SettingsActivity
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.URL_GITHUB_WIKI_DOWNLOAD
import me.devsaki.hentoid.core.initDrawerLayout
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.database.domains.urlsAreSame
import me.devsaki.hentoid.databinding.ActivityBrowserBinding
import me.devsaki.hentoid.enums.AlertStatus
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.AppRepoInfoEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.events.DownloadPreparationEvent
import me.devsaki.hentoid.fragments.browser.BookmarksDrawerFragment
import me.devsaki.hentoid.fragments.browser.DuplicateDialogFragment
import me.devsaki.hentoid.fragments.browser.UrlDialogFragment
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.ui.invokeNumberInputDialog
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.calcPhash
import me.devsaki.hentoid.util.copyPlainTextToClipboard
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.getAssetAsString
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.findDuplicate
import me.devsaki.hentoid.util.getBlockedTags
import me.devsaki.hentoid.util.getCenter
import me.devsaki.hentoid.util.getCoverBitmapFromStream
import me.devsaki.hentoid.util.getFixedContext
import me.devsaki.hentoid.util.getHashEngine
import me.devsaki.hentoid.util.isInLibrary
import me.devsaki.hentoid.util.isInQueue
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import me.devsaki.hentoid.util.network.simplifyUrl
import me.devsaki.hentoid.util.openReader
import me.devsaki.hentoid.util.parseDownloadParams
import me.devsaki.hentoid.util.setMargins
import me.devsaki.hentoid.util.showTooltip
import me.devsaki.hentoid.util.snack
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.tryShowMenuIcons
import me.devsaki.hentoid.util.useLegacyInsets
import me.devsaki.hentoid.viewmodels.BrowserViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.views.NestedScrollWebView
import me.devsaki.hentoid.widget.showAddQueueMenu
import me.devsaki.hentoid.widget.showDownloadModeMenu
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.util.Locale
import kotlin.math.round

/**
 * Browser activity which allows the user to navigate a supported source.
 * No particular source should be filtered/defined here.
 * The source itself should contain every method it needs to function.
 */
private val GALLERY_REGEX by lazy { "\\b|/galleries|/gallery|/g|/entry\\b".toRegex() }

private const val SIMILARITY_MIN_THRESHOLD = 0.85f

abstract class BaseBrowserActivity : BaseActivity(), CustomWebViewClient.BrowserActivity,
    DuplicateDialogFragment.Parent, BookmarksDrawerFragment.Parent {

    protected enum class ActionMode {
        // Download book
        DOWNLOAD,  // Download new pages
        DOWNLOAD_PLUS,  // Go to the queue screen
        VIEW_QUEUE,  // Read downloaded book (image viewer)
        READ
    }

    private enum class ContentStatus {
        // Content is undownloadable
        UNDOWNLOADABLE,  // Content is unknown (i.e. ready to be downloaded)
        UNKNOWN,  // Content is already in the library
        IN_COLLECTION,  // Content is already queued
        IN_QUEUE
    }

    protected enum class SeekMode {
        // Seek a specific results page
        PAGE,  // Back to latest gallery page
        GALLERY
    }


    // === COMMUNICATION
    private lateinit var webClient: CustomWebViewClient

    private var callback: OnBackPressedCallback? = null

    private val settingsListener =
        OnSharedPreferenceChangeListener { _, key: String? ->
            onSharedPreferenceChanged(key ?: "")
        }

    private lateinit var viewModel: BrowserViewModel


    // === UI
    protected var binding: ActivityBrowserBinding? = null

    // Dynamically generated webview
    protected lateinit var webView: NestedScrollWebView

    // Top toolbar buttons
    private var refreshStopMenu: MenuItem? = null
    private var bookmarkMenu: MenuItem? = null
    private var adblockMenu: MenuItem? = null

    @DrawableRes
    private var downloadIcon = 0
    protected var languageFilterButton: FloatingActionButton? = null


    // === CURRENTLY VIEWED CONTENT-RELATED VARIABLES
    private var currentContent: Content? = null

    // Content ID of the duplicate candidate of the currently viewed Content
    private var duplicateId: Long = -1

    // Similarity score of the duplicate candidate of the currently viewed Content
    private var duplicateSimilarity = 0f

    // Title of the browsed content; valued if extra images have been detected
    private var onlineContentTitle = ""

    // Blocked tags found on the currently viewed Content
    private var blockedTags: MutableList<String> = mutableListOf()

    // Extra images found on the currently viewed Content
    private var extraImages: MutableList<ImageFile> = mutableListOf()

    // List of URLs of downloaded books for the current site
    private val downloadedBooksUrls: MutableList<String> = ArrayList()

    // List of URLs of merged books for the current site
    private val mergedBooksUrls: MutableList<String> = ArrayList()

    // List of URLs of queued books for the current site
    private val queuedBooksUrls: MutableList<String> = ArrayList()

    // List of tags of Preference-browser-blocked tags
    private var internalPrefBlockedTags: MutableList<String> = ArrayList()

    // === CACHE FOR OTHER ELEMENTS

    private val bookmarks = ArrayList<SiteBookmark>()

    // === OTHER VARIABLES
    // Indicates which mode the download button is in
    protected var actionButtonMode: ActionMode? = null

    // Indicates which mode the seek button is in
    private var seekButtonMode: SeekMode? = null

    // Alert to be displayed
    private var alert: UpdateInfo.SourceAlert? = null

    // Handler for fetch interceptor
    protected var isManagedFetch = false
    protected var fetchHandler: BiConsumer<String, String>? = null
    protected var fetchResponseHandler: Consumer<String>? = null
    private var fetchResponseCallback: Consumer<String>? = null
    protected var xhrHandler: BiConsumer<String, String>? = null
    private var fetchInterceptorScript: String? = null
    private var xhrInterceptorScript: String? = null
    private var internalCustomCss: String? = null

    protected var interceptServiceWorker = false

    protected abstract fun createWebClient(): CustomWebViewClient

    abstract fun getStartSite(): Site


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        useLegacyInsets()
        setContentView(binding!!.root)
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            startActivity(Intent(this, MissingWebViewActivity::class.java))
            return
        }
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        Settings.registerPrefsChangedListener(settingsListener)
        if (Settings.isBrowserMarkDownloaded) updateDownloadedBooksUrls()
        if (Settings.isBrowserMarkMerged) updateMergedBooksUrls()
        if (Settings.isBrowserMarkQueued) updateQueuedBooksUrls()
        if (Settings.isBrowserMarkBlockedTags) updatePrefBlockedTags()
        Timber.d("Loading site: %s", getStartSite())

        // Toolbar
        // Top toolbar
        val toolbar = binding!!.toolbar
        binding?.drawerLayout?.let { dl ->
            initDrawerLayout(dl, toolbar)
            if (Settings.isBrowserLockFavPanel)
                dl.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        }
        // Signal current site to navigation drawer for it to check it
        EventBus.getDefault().post(
            CommunicationEvent(
                CommunicationEvent.Type.SIGNAL_SITE,
                CommunicationEvent.Recipient.DRAWER,
                getStartSite().name
            )
        )

        tryShowMenuIcons(this, toolbar.menu)
        toolbar.setOnMenuItemClickListener { this.onMenuItemSelected(it) }
        toolbar.title = getStartSite().description
        toolbar.setOnClickListener { getStartUrl(true) { loadUrl(it) } }
        addCustomBackControl()

        refreshStopMenu = toolbar.menu.findItem(R.id.web_menu_refresh_stop)
        bookmarkMenu = toolbar.menu.findItem(R.id.web_menu_bookmark)
        adblockMenu = toolbar.menu.findItem(R.id.web_menu_adblocker)
        binding?.apply {
            this@BaseBrowserActivity.languageFilterButton = languageFilterButton
            bottomNavigation.setOnMenuItemClickListener { item ->
                this@BaseBrowserActivity.onMenuItemSelected(item)
            }
            menuLibrary.setOnClickListener { onLibraryClick() }
            menuBack.setOnClickListener { onBackClick() }
            menuSeek.setOnClickListener { onSeekClick() }
            menuForward.setOnClickListener { onForwardClick() }
            actionButton.setOnClickListener { onActionClick() }

            if (getStartSite() == Site.NONE) initWelcome()
        }

        // Webview
        initWebview()
        initSwipeLayout()
        getStartUrl { webView.loadUrl(it) }
        if (!Settings.recentVisibility) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // Banner close buttons
        val topAlertCloseButton = findViewById<View>(R.id.top_alert_close_btn)
        topAlertCloseButton.setOnClickListener { binding?.topAlert?.visibility = View.GONE }

        val bottomAlertCloseButton = findViewById<View>(R.id.bottom_alert_close_btn)
        bottomAlertCloseButton.setOnClickListener { onBottomAlertCloseClick() }
        downloadIcon =
            if (Settings.getBrowserDlAction() == DownloadMode.STREAM) R.drawable.selector_download_stream_action else R.drawable.selector_download_action
        if (Settings.isBrowserMode) downloadIcon = R.drawable.ic_forbidden_disabled
        binding?.actionButton?.setImageDrawable(ContextCompat.getDrawable(this, downloadIcon))
        displayTopAlertBanner()
        updateAdblockButton(
            Settings.isAdBlockerOn(getStartSite()) &&
                    Settings.isBrowserAugmented(getStartSite())
        )

        viewModel =
            ViewModelProvider(
                this@BaseBrowserActivity,
                ViewModelFactory(application)
            )[BrowserViewModel::class.java]

        viewModel.bookmarks().observe(this)
        {
            bookmarks.clear()
            bookmarks.addAll(it)
        }

        viewModel.setBrowserSite(getStartSite())
        viewModel.loadBookmarks()
    }

    private fun addCustomBackControl() {
        callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Close drawers
                binding?.apply {
                    if (root.isDrawerOpen(GravityCompat.START) || root.isDrawerOpen(GravityCompat.END)) {
                        EventBus.getDefault()
                            .post(CommunicationEvent(CommunicationEvent.Type.CLOSE_DRAWER))
                        return
                    }
                }

                // Previous webpage
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }

                // Other cases
                callback?.remove()
                onBackPressedDispatcher.onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback!!)
    }

    override fun onDestroy() {
        webClient.destroy()

        webView.apply {
            // the WebView must be removed from the view hierarchy before calling destroy
            // to prevent a memory leak
            // See https://developer.android.com/reference/android/webkit/WebView.html#destroy%28%29
            (parent as ViewGroup).removeView(this)
            removeAllViews()
            destroy()
        }
        Settings.unregisterPrefsChangedListener(settingsListener)

        // Cancel any previous extra page load
        EventBus.getDefault().post(
            DownloadCommandEvent(
                DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT,
                currentContent
            )
        )
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        binding = null
        super.onDestroy()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        val url = webView.url
        if (url != null) {
            val bundle = BaseBrowserActivityBundle()
            if (WebkitPackageHelper.getWebViewAvailable()) bundle.url = url
            outState.putAll(bundle.bundle)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        val url = BaseBrowserActivityBundle(savedInstanceState).url
        if (url.isNotEmpty()) webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            startActivity(Intent(this, MissingWebViewActivity::class.java))
            return
        }

        checkPermissions()

        // Refresh navigation drawer display
        EventBus.getDefault().post(
            CommunicationEvent(
                CommunicationEvent.Type.SIGNAL_SITE,
                CommunicationEvent.Recipient.DRAWER,
                getStartSite().name
            )
        )

        webView.url?.let { url ->
            Timber.i(">> WebActivity resume : $url ${currentContent != null} ${currentContent?.title ?: ""}")
            if (!webClient.isGalleryPage(url)) return

            // TODO Cancel whichever process was happening before
            currentContent?.let { cc ->
                lifecycleScope.launch {
                    try {
                        val status = processContent(cc, false)
                        onContentProcessed(cc, status, false)
                    } catch (t: Throwable) {
                        Timber.e(t)
                        onContentProcessed(cc, ContentStatus.UNKNOWN, false)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!WebkitPackageHelper.getWebViewAvailable()) return
        Timber.i("onPause")
        webView.url?.let {
            viewModel.saveToHistory(getStartSite(), it)
        }
    }

    /**
     * Determine the URL the browser will load at startup
     * - Either a URL specifically given to the activity (e.g. "view source" action)
     * - Or the last viewed page, if the setting is enabled
     * - If neither of the previous cases, the default URL of the site
     *
     * @param forceHomepage Force the URL to be the current site's homepage
     *
     * @return URL to load at startup
     */
    private fun getStartUrl(
        forceHomepage: Boolean = false,
        onFound: Consumer<String>
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao: CollectionDAO = ObjectBoxDAO()
            val site = getStartSite()
            var result = ""
            try {
                // Special case : app's welcome page
                if (Site.NONE == getStartSite()) return@launch

                if (!forceHomepage) {
                    // Priority 1 : URL specifically given to the activity (e.g. "view source" action)
                    if (intent.extras != null) {
                        val bundle = BaseBrowserActivityBundle(intent.extras!!)
                        result = bundle.url
                    }

                    // Priority 2 : Last viewed position, if setting enabled
                    if (result.isBlank() && Settings.isBrowserResumeLast) {
                        val siteHistory = dao.selectLastHistory(getStartSite())
                        result = siteHistory.url
                    }
                    var reason = ""
                    if (result.isNotBlank()) {
                        try {
                            val uri = result.toUri()
                            if (webClient.isOutsideRestrictedDomains(uri)) {
                                reason = " (" + resources.getString(
                                    R.string.web_target_page_outside_restricted_domains,
                                    uri.host ?: ""
                                ) + ")"
                            } else {
                                val headers: MutableList<Pair<String, String>> = ArrayList()
                                headers.add(Pair(HEADER_REFERER_KEY, site.url))
                                val cookieStr = getCookies(result)
                                if (cookieStr.isNotEmpty())
                                    headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
                                val response = getOnlineResourceFast(
                                    result,
                                    headers,
                                    site.useMobileAgent,
                                    site.useHentoidAgent,
                                    site.useWebviewAgent
                                )
                                if (response.code < 300) {
                                    withContext(Dispatchers.Main) { onFound(result) }
                                    return@launch
                                } else reason = " (HTTP ${response.code})"
                            }
                        } catch (e: Exception) {
                            Timber.i(e, "Unavailable resource$reason : $result")
                            reason = " (${e.javaClass.name})"
                        }
                    }
                    if (reason.isNotBlank()) snack(
                        resources.getString(
                            R.string.web_target_page_unavailable,
                            reason
                        )
                    )
                }

                // Priority 3 : Homepage (manually set through bookmarks or default)
                val welcomePage = dao.selectHomepage(getStartSite())
                withContext(Dispatchers.Main) { onFound(welcomePage?.url ?: getStartSite().url) }
            } finally {
                dao.cleanup()
            }
        }
    }

    @SuppressLint("NonConstantResourceId")
    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.web_menu_bookmark -> onBookmarkClick()
            R.id.web_menu_refresh_stop -> onRefreshStopClick()
            R.id.web_menu_settings -> onSettingsClick()
            R.id.web_menu_adblocker -> onAdblockClick()
            R.id.web_menu_url -> onManageLinkClick()
            R.id.help -> startBrowserActivity(URL_GITHUB_WIKI_DOWNLOAD)
            else -> {
                return false
            }
        }
        return true
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun onUpdateEvent(event: AppRepoInfoEvent) {
        if (event.sourceAlerts.containsKey(getStartSite())) {
            alert = event.sourceAlerts[getStartSite()]
            displayTopAlertBanner()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun onDownloadPreparationEvent(event: DownloadPreparationEvent) {
        // Show progress if it's about current content or its best duplicate
        if (currentContent != null && isInLibrary(currentContent!!.status) && event.getRelevantId() == currentContent!!.id || duplicateId > 0 && event.getRelevantId() == duplicateId) {
            binding?.apply {
                progressBar.max = 100
                progressBar.progress = round(event.progress * 100).toInt()
                progressBar.progressTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(baseContext, R.color.secondary_light)
                )
                progressBar.visibility = View.VISIBLE
            }
        }
    }

    // Make sure permissions are set at resume time; if not, warn the user
    private fun checkPermissions() {
        if (Settings.isBrowserMode) return
        if (!this.requestExternalStorageReadWritePermission(RQST_STORAGE_PERMISSION))
            toast(R.string.web_storage_permission_denied)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebview() {
        webView = try {
            NestedScrollWebView(this)
        } catch (_: NotFoundException) {
            // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
            // Creating with the application Context fixes this, but is not generally recommended for view creation
            NestedScrollWebView(getFixedContext(this))
        }
        webView.isHapticFeedbackEnabled = false
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding?.apply {
                    if (newProgress == 100) {
                        swipeContainer.post {
                            binding?.swipeContainer?.isRefreshing = false
                        }
                    } else {
                        swipeContainer.post {
                            binding?.swipeContainer?.isRefreshing = true
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        webClient = createWebClient()
        webView.webViewClient = webClient
        if (getStartSite().useManagedRequests || Settings.proxy.isNotEmpty() || Settings.dnsOverHttps > -1) {
            xhrHandler = { url, body -> webClient.recordDynamicPostRequests(url, body) }
            enableStandardFetchHandler()
        }

        // Download immediately on long click on a link / image link
        if (Settings.isBrowserQuickDl) {
            webView.setOnLongTapListener { x: Int, y: Int ->
                onLongTap(x, y)
            }
            webView.setLongClickThreshold(Settings.browserQuickDlThreshold)
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        val webSettings = webView.settings
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        Timber.i("%s : using user-agent %s", getStartSite().name, getStartSite().userAgent)
        webSettings.userAgentString = getStartSite().userAgent
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        fetchHandler?.let { webView.addJavascriptInterface(FetchHandler(it), "fetchHandler") }
        if (isManagedFetch) {
            val responseHandler =
                { responseBody: String -> fetchResponseCallback?.invoke(responseBody) ?: Unit }
            webView.addJavascriptInterface(
                FetchResponseHandler(responseHandler),
                "fetchResponseHandler"
            )
        }
        xhrHandler?.let { webView.addJavascriptInterface(XhrHandler(it), "xhrHandler") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            webSettings.isAlgorithmicDarkeningAllowed =
                (!Settings.isBrowserForceLightMode && Settings.colorTheme != Settings.Value.COLOR_THEME_LIGHT)
        }
        if (interceptServiceWorker && WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        return webClient.shouldInterceptRequest(webView, request)
                    }
                })
        }
    }

    private fun enableStandardFetchHandler() {
        if (null == fetchHandler)
            fetchHandler = { url, body -> webClient.recordDynamicPostRequests(url, body) }
    }

    private fun initSwipeLayout() {
        val layoutParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding?.apply {
            swipeContainer.addView(webView, layoutParams)
            swipeContainer.setOnRefreshListener { if (!swipeContainer.isRefreshing || !webClient.isLoading()) webView.reload() }
            swipeContainer.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
            )
        }
    }

    private fun initWelcome() {
        binding?.apply {
            val linkMenu = toolbar.menu.findItem(R.id.web_menu_url)
            swipeContainer.isVisible = false
            welcome.isVisible = true
            bottomNavigation.isVisible = false
            adblockMenu?.isVisible = false
            refreshStopMenu?.isVisible = false
            linkMenu?.isVisible = false
            toolbar.setTitle(R.string.title_activity_browser)
        }
    }

    private fun onLongTap(x: Int, y: Int) {
        if (Settings.isBrowserMode) return
        val result = webView.hitTestResult
        // Plain link
        val url: String? =
            if (result.type == HitTestResult.SRC_ANCHOR_TYPE && result.extra != null) {
                result.extra
            } else if (result.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val handler = Handler(mainLooper)
                val message = handler.obtainMessage()
                webView.requestFocusNodeHref(message)
                message.data.getString("url")
            } else {
                null
            }
        if (!url.isNullOrEmpty() && webClient.isGalleryPage(url)) {
            binding?.apply {
                setMargins(
                    quickDlFeedback,
                    x - quickDlFeedback.width / 2,
                    y - quickDlFeedback.height / 2 + topBar.bottom,
                    0,
                    0
                )
                quickDlFeedback.setIndicatorColor(
                    ContextCompat.getColor(
                        baseContext,
                        R.color.medium_gray
                    )
                )
                quickDlFeedback.visibility = View.VISIBLE
            }

            webClient.flagAsQuickDownload(url)
            browserFetch(url)
        }
    }

    override fun onPageStarted(
        url: String,
        isGalleryPage: Boolean,
        isHtmlLoaded: Boolean,
        isBrowsable: Boolean
    ) {
        refreshStopMenu?.setIcon(R.drawable.ic_close)
        binding?.progressBar?.visibility = View.GONE
        if (!isHtmlLoaded) {
            Timber.v("onPageStarted $url")
            lifecycleScope.launch { setActionMode(null) }
        }

        // Activate fetch handler
        if (fetchHandler != null || fetchResponseHandler != null || isManagedFetch) {
            if (null == fetchInterceptorScript) fetchInterceptorScript =
                webClient.getAssetJsScript(this, "fetch_override.js", null)
            webView.loadUrl(fetchInterceptorScript!!)
        }
        // Activate XHR handler
        if (xhrHandler != null) {
            if (null == xhrInterceptorScript) xhrInterceptorScript =
                webClient.getAssetJsScript(this, "xhr_override.js", null)
            webView.loadUrl(xhrInterceptorScript!!)
        }

        if (isBrowsable) {
            viewModel.setPageUrl(url)

            // Display download button tooltip if a book page has been reached
            if (isGalleryPage && !Settings.isBrowserMode) tooltip(
                R.string.help_web_download,
                false
            )

            // Update bookmark button
            val currentBookmark = bookmarks.firstOrNull { urlsAreSame(it.url, url) }
            updateBookmarkButton(currentBookmark != null)
        }
    }

    // WARNING : This method may not be called from the UI thread
    override fun onGalleryPageStarted(url: String) {
        blockedTags.clear()
        extraImages.clear()
        duplicateId = -1
        duplicateSimilarity = 0f
        // Cancel any previous extra page load
        EventBus.getDefault().post(
            DownloadCommandEvent(
                DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT,
                currentContent
            )
        )
        // Greys out the action button
        // useful for sites with JS loading that do not trigger onPageStarted (e.g. Luscious, Pixiv)
        Timber.v("onGalleryPageStarted $url")
        lifecycleScope.launch { setActionMode(null) }
    }

    override fun onPageFinished(url: String, isResultsPage: Boolean, isGalleryPage: Boolean) {
        refreshNavigationMenu(isResultsPage)
        refreshStopMenu?.setIcon(R.drawable.ic_action_refresh)

        // Manage bottom alert banner visibility
        if (isGalleryPage) displayBottomAlertBanner(blockedTags) // Called here to be sure it is displayed on the gallery page
        else onBottomAlertCloseClick()

        viewModel.setPageTitle(webView.title ?: "")
        webView.url?.let {
            viewModel.saveToHistory(getStartSite(), it)
        }
    }

    /**
     * Refresh the visuals of the buttons of the navigation menu
     */
    private fun refreshNavigationMenu(isResultsPage: Boolean) {
        binding?.apply {
            menuBack.isEnabled = webView.canGoBack()
            menuForward.isEnabled = webView.canGoForward()
        }
        changeSeekMode(
            if (isResultsPage) SeekMode.PAGE else SeekMode.GALLERY,
            isResultsPage || backListContainsGallery(webView.copyBackForwardList()) > -1
        )
    }

    /**
     * Displays the top alert banner
     * (the one that contains the alerts when downloads are broken or sites are unavailable)
     */
    private fun displayTopAlertBanner() {
        alert?.let {
            binding?.apply {
                topAlertIcon.setImageResource(it.getStatus().icon)
                topAlertTxt.text = formatAlertMessage(it)
                topAlert.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Displays the bottom alert banner
     * (the one that contains the alerts when reaching a book with unwanted tags)
     */
    private fun displayBottomAlertBanner(unwantedTags: List<String?>) {
        binding?.apply {
            if (unwantedTags.isNotEmpty()) {
                bottomAlertTxt.text = resources.getString(
                    R.string.alert_unwanted_tags,
                    TextUtils.join(", ", unwantedTags)
                )
                bottomAlert.visibility = View.VISIBLE
            } else {
                bottomAlert.visibility = View.GONE
            }
        }
    }

    /**
     * Handler for the close icon of the bottom alert banner
     */
    private fun onBottomAlertCloseClick() {
        binding?.bottomAlert?.visibility = View.GONE
    }

    /**
     * Handler for the "Home" navigation button
     */
    @Suppress("DEPRECATION")
    private fun onLibraryClick() {
        val intent = Intent(this, LibraryActivity::class.java)
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
        finish()
    }

    /**
     * Handler for the "back" navigation button of the browser
     */
    private fun onBackClick() {
        webView.goBack()
    }

    /**
     * Handler for the "forward" navigation button of the browser
     */
    private fun onForwardClick() {
        webView.goForward()
    }

    /**
     * Handler for the "back to gallery page" / "seek page" navigation button of the browser
     */
    private fun onSeekClick() {
        if (SeekMode.GALLERY == seekButtonMode) {
            val list = webView.copyBackForwardList()
            val galleryIndex = backListContainsGallery(list)
            if (galleryIndex > -1) webView.goBackOrForward(galleryIndex - list.currentIndex)
        } else { // Seek to page
            invokeNumberInputDialog(this, R.string.goto_page) { goToPage(it) }
        }
    }

    /**
     * Go to the given page number
     *
     * @param pageNum Page number to go to (1-indexed)
     */
    private fun goToPage(pageNum: Int) {
        val url = webView.url
        if (pageNum < 1 || null == url) return
        val newUrl = webClient.seekResultsUrl(url, pageNum)
        webView.loadUrl(newUrl)
    }

    /**
     * Handler for the "bookmark" top menu button of the browser
     */
    private fun onBookmarkClick() {
        binding?.drawerLayout?.openDrawer(GravityCompat.END)
    }

    /**
     * Handler for the "refresh page/stop refreshing" button of the browser
     */
    private fun onRefreshStopClick() {
        if (webClient.isLoading()) webView.stopLoading() else webView.reload()
    }

    /**
     * Handler for the "Adblocker" button
     */
    private fun onAdblockClick() {
        val initialValue =
            Settings.isAdBlockerOn(getStartSite()) && Settings.isBrowserAugmented(getStartSite())
        Settings.setAdBlockerOn(getStartSite(), !initialValue)
    }

    /**
     * Handler for the "Manage link" button
     */
    private fun onManageLinkClick() {
        val url = webView.url ?: ""
        if (copyPlainTextToClipboard(this, url)) toast(R.string.web_url_clipboard)
        UrlDialogFragment.invoke(this, url)
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun downloadContentArchivePdf(url: String) {
        currentContent?.let { processDownload(it, archiveUrl = url) }
    }

    override fun updateBookmarkButton(newValue: Boolean) {
        if (newValue) bookmarkMenu?.setIcon(R.drawable.ic_bookmark_full)
        else bookmarkMenu?.setIcon(R.drawable.ic_bookmark)
    }

    private fun updateAdblockButton(newValue: Boolean) {
        val targetTxt = if (newValue) R.string.web_adblocker_on else R.string.web_adblocker_off
        val targetIco = if (newValue) R.drawable.ic_shield_on else R.drawable.ic_shield_off
        adblockMenu?.icon = ContextCompat.getDrawable(this, targetIco)
        adblockMenu?.title = resources.getString(targetTxt)
    }

    /**
     * Listener for the Action button : download content, view queue or read content
     */
    protected open fun onActionClick() {
        val theContent = currentContent ?: return
        val needsDuplicateAlert =
            Settings.downloadDuplicateAsk && duplicateSimilarity >= SIMILARITY_MIN_THRESHOLD
        when (actionButtonMode) {
            ActionMode.DOWNLOAD -> {
                if (needsDuplicateAlert) DuplicateDialogFragment.invoke(
                    this,
                    duplicateId,
                    currentContent!!.qtyPages,
                    duplicateSimilarity,
                    false
                ) else processDownload(theContent)
            }

            ActionMode.DOWNLOAD_PLUS -> {
                if (needsDuplicateAlert) DuplicateDialogFragment.invoke(
                    this,
                    duplicateId,
                    currentContent!!.qtyPages,
                    duplicateSimilarity,
                    true
                ) else processDownload(theContent, isDownloadPlus = true)
            }

            ActionMode.VIEW_QUEUE -> goToQueue()
            ActionMode.READ -> {
                val searchUrl =
                    if (getStartSite().hasCoverBasedPageUpdates) currentContent!!.coverImageUrl else ""
                val dao: CollectionDAO = ObjectBoxDAO()
                try {
                    currentContent = dao.selectContentByUrlOrCover(
                        currentContent!!.site,
                        currentContent!!.url,
                        searchUrl
                    )
                    if (currentContent != null && (StatusContent.DOWNLOADED == currentContent!!.status || StatusContent.ERROR == currentContent!!.status || StatusContent.MIGRATED == currentContent!!.status))
                        openReader(
                            this, currentContent!!, -1, null,
                            forceShowGallery = false,
                            newTask = false
                        )
                    else {
                        lifecycleScope.launch { setActionMode(null) }
                    }
                } finally {
                    dao.cleanup()
                }
            }

            else -> {
                // Nothing
            }
        }
    }

    /**
     * Switch the action button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private suspend fun setActionMode(mode: ActionMode?) = withContext(Dispatchers.Main) {
        Timber.v("actionMode $mode")
        binding?.apply {
            if (Settings.isBrowserMode || null == mode) {
                actionButton.setImageDrawable(
                    ContextCompat.getDrawable(
                        this@BaseBrowserActivity,
                        R.drawable.ic_forbidden_disabled
                    )
                )
                actionButton.visibility = View.INVISIBLE
                actionBtnBadge.visibility = View.INVISIBLE
                return@withContext
            }
            @DrawableRes val resId: Int = when (mode) {
                ActionMode.DOWNLOAD, ActionMode.DOWNLOAD_PLUS -> downloadIcon
                ActionMode.VIEW_QUEUE -> R.drawable.ic_action_queue
                ActionMode.READ -> R.drawable.ic_action_play
            }
            actionButtonMode = mode
            actionButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this@BaseBrowserActivity,
                    resId
                )
            )
            actionButton.visibility = View.VISIBLE
            // It will become visible whenever the count of extra pages is known
            if (ActionMode.DOWNLOAD_PLUS != mode) actionBtnBadge.visibility = View.INVISIBLE
        }
    }

    /**
     * Switch the seek button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private fun changeSeekMode(mode: SeekMode, enabled: Boolean) {
        @DrawableRes var resId: Int = R.drawable.ic_action_back_gallery
        if (SeekMode.PAGE == mode) resId = R.drawable.ic_page_seek
        seekButtonMode = mode
        binding?.apply {
            menuSeek.setIconResource(resId)
            menuSeek.isEnabled = enabled
            menuSeek.isVisible = true
        }
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     *
     * @param quickDownload      True if the action has been triggered by a quick download
     * (which means we're not on a book gallery page but on the book list page)
     * @param isDownloadPlus     True if the action has been triggered by a "download extra pages" action
     * @param isReplaceDuplicate True if the action has been triggered by a "download and replace existing duplicate book" action
     * @param archiveUrl         Not null if the current content should be downloaded as an archive with the given Url
     */
    fun processDownload(
        content: Content,
        quickDownload: Boolean = false,
        isDownloadPlus: Boolean = false,
        isReplaceDuplicate: Boolean = false,
        archiveUrl: String? = null
    ) {
        val dao: CollectionDAO = ObjectBoxDAO()
        var theContent = if (content.id > 0) dao.selectContent(content.id) else currentContent
        if (null == theContent) return
        if (!isDownloadPlus && StatusContent.DOWNLOADED == theContent.status) {
            toast(R.string.already_downloaded)
            if (!quickDownload) lifecycleScope.launch { setActionMode(ActionMode.READ) }
            return
        }
        var replacementTitle: String? = null
        if (isDownloadPlus) {
            // Copy the _current_ content's download params to the extra images
            val downloadParamsStr = theContent.downloadParams
            if (downloadParamsStr.length > 2) {
                for (i in extraImages) i.downloadParams = downloadParamsStr
            }

            // Determine base book : browsed downloaded book or best duplicate ?
            if (!isInLibrary(theContent.status) && duplicateId > 0) {
                theContent = dao.selectContent(duplicateId)
                if (null == theContent) return
            }

            // Append additional pages & chapters to the base book's list of pages & chapters
            val updatedImgs: MutableList<ImageFile> = ArrayList() // Entire image set to update
            val existingImageUrls: MutableSet<String> = HashSet() // URLs of known images
            val existingChapterOrders: MutableSet<Int> = HashSet() // Positions of known chapters
            theContent.imageFiles.let {
                existingImageUrls.addAll(it.map { img -> img.url })
                existingChapterOrders.addAll(
                    it.map { img ->
                        if (null == img.chapter.target) return@map -1
                        img.chapter.target.order
                    }
                )
                updatedImgs.addAll(it)
            }

            // Save additional pages references to stored book, without duplicate URLs
            val additionalNonExistingImages = extraImages.filterNot { img ->
                existingImageUrls.contains(img.url)
            }
            if (additionalNonExistingImages.isNotEmpty()) {
                updatedImgs.addAll(additionalNonExistingImages)
                theContent.setImageFiles(updatedImgs)
                // Update content title if extra pages are found and title has changed
                if (onlineContentTitle.isNotEmpty()
                    && !onlineContentTitle.equals(theContent.title, ignoreCase = true)
                ) replacementTitle = onlineContentTitle
            }
            // Save additional chapters to stored book
            val additionalNonExistingChapters =
                additionalNonExistingImages.mapNotNull { it.linkedChapter }
                    .filterNot { ch -> existingChapterOrders.contains(ch.order) }
            if (additionalNonExistingChapters.isNotEmpty()) {
                val updatedChapters = theContent.chaptersList.toMutableList()
                updatedChapters.addAll(additionalNonExistingChapters)
                theContent.setChapters(updatedChapters)
            }
            theContent.status = StatusContent.SAVED
            dao.insertContent(theContent)
        } // isDownloadPlus

        // Check if the tag blocker applies here
        val blockedTagsLocal = getBlockedTags(theContent.id, dao)
        if (blockedTagsLocal.isNotEmpty()) {
            if (Settings.tagBlockingBehaviour == Settings.Value.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE) { // Stop right here
                toast(R.string.blocked_tag, blockedTagsLocal[0])
            } else { // Insert directly as an error
                val errors: MutableList<ErrorRecord> = ArrayList()
                errors.add(
                    ErrorRecord(
                        type = ErrorType.BLOCKED,
                        url = theContent.url,
                        contentPart = "tags",
                        description = "blocked tags : " + TextUtils.join(", ", blockedTagsLocal),
                        timestamp = Instant.now()
                    )
                )
                theContent.setErrorLog(errors)
                theContent.downloadMode = Settings.getBrowserDlAction()
                theContent.status = StatusContent.ERROR
                if (isReplaceDuplicate) theContent.setContentIdToReplace(duplicateId)
                dao.insertContent(theContent)
                toast(R.string.blocked_tag_queued, blockedTagsLocal[0])
                lifecycleScope.launch { setActionMode(ActionMode.VIEW_QUEUE) }
            }
            return
        }
        val replacementTitleFinal = replacementTitle
        // No reason to block or ignore -> actually add to the queue
        if (Settings.queueNewDownloadPosition == Settings.Value.QUEUE_NEW_DOWNLOADS_POSITION_ASK && Settings.getBrowserDlAction() == DownloadMode.ASK) {
            showAddQueueMenu(this, webView, this) { position1, _ ->
                showDownloadModeMenu(
                    this,
                    webView, this,
                    { _, item ->
                        addToQueue(
                            theContent,
                            if (0 == position1) QueuePosition.TOP else QueuePosition.BOTTOM,
                            DownloadMode.fromValue(item.tag as Int),
                            isReplaceDuplicate,
                            replacementTitleFinal,
                            archiveUrl
                        )
                    }, content.site.shouldBeStreamed, null
                )
            }
        } else if (Settings.queueNewDownloadPosition == Settings.Value.QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            showAddQueueMenu(this, webView, this) { position, _ ->
                addToQueue(
                    theContent,
                    if (0 == position) QueuePosition.TOP else QueuePosition.BOTTOM,
                    Settings.getBrowserDlAction(),
                    isReplaceDuplicate,
                    replacementTitleFinal,
                    archiveUrl
                )
            }
        } else if (Settings.getBrowserDlAction() == DownloadMode.ASK) {
            showDownloadModeMenu(
                this, webView, this, { _, item ->
                    addToQueue(
                        theContent,
                        QueuePosition.entries.first { it.value == Settings.queueNewDownloadPosition },
                        DownloadMode.fromValue(item.tag as Int),
                        isReplaceDuplicate,
                        replacementTitleFinal,
                        archiveUrl
                    )
                }, content.site.shouldBeStreamed, null
            )
        } else {
            addToQueue(
                theContent,
                QueuePosition.entries.first { it.value == Settings.queueNewDownloadPosition },
                Settings.getBrowserDlAction(),
                isReplaceDuplicate,
                replacementTitleFinal,
                archiveUrl
            )
        }
        dao.cleanup()
    }

    /**
     * Add the given content to the downloads queue
     * @param content            Content to add to the queue
     * @param position           Target position in the queue (top or bottom)
     * @param downloadMode       Download mode for this content
     * @param isReplaceDuplicate True if existing duplicate book has to be replaced upon download completion
     * @param replacementTitle   Replacement title to apply to the new download
     * @param archiveUrl         Not null if the current content should be downloaded as an archive with the given Url
     */
    private fun addToQueue(
        content: Content,
        position: QueuePosition,
        downloadMode: DownloadMode,
        isReplaceDuplicate: Boolean,
        replacementTitle: String? = null,
        archiveUrl: String? = null
    ) {
        Timber.i("Adding to queue  ${content.url} ${content.galleryUrl}")
        binding?.apply {
            val coords = getCenter(quickDlFeedback)
            if (coords != null && View.VISIBLE == quickDlFeedback.visibility) {
                setMargins(
                    animatedCheck,
                    coords.x - animatedCheck.width / 2,
                    coords.y - animatedCheck.height / 2,
                    0,
                    0
                )
            } else {
                setMargins(
                    animatedCheck,
                    webView.width / 2 - animatedCheck.width / 2,
                    webView.height / 2 - animatedCheck.height / 2, 0, 0
                )
            }
            animatedCheck.visibility = View.VISIBLE
            (animatedCheck.drawable as Animatable).start()
            Handler(mainLooper).postDelayed({
                if (binding != null) animatedCheck.visibility = View.GONE
            }, 1000)
        }
        content.downloadMode = downloadMode
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            dao.addContentToQueue(
                content,
                null,
                null,
                position,
                if (isReplaceDuplicate) duplicateId else -1,
                replacementTitle,
                archiveUrl,
                isQueueActive(this)
            )
        } finally {
            dao.cleanup()
        }
        if (Settings.isQueueAutostart) resumeQueue(this)
        lifecycleScope.launch { setActionMode(ActionMode.VIEW_QUEUE) }
        if (webClient.isMarkQueued()) updateQueuedBooksUrls()
    }

    /**
     * Take the user to the queue screen
     */
    @Suppress("DEPRECATION")
    private fun goToQueue() {
        val intent = Intent(this, QueueActivity::class.java)
        if (currentContent != null) {
            val builder = QueueActivityBundle()
            builder.contentHash = currentContent!!.uniqueHash()
            builder.isErrorsTab = currentContent!!.status == StatusContent.ERROR
            intent.putExtras(builder.bundle)
        }
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Display webview controls according to designated content
     *
     * @param onlineContent Currently displayed content
     * @param quickDownload True if the action has been triggered by a quick download (long tap)
     *
     * @return The status of the Content after being processed
     */
    private suspend fun processContent(
        onlineContent: Content,
        quickDownload: Boolean
    ): ContentStatus = withContext(Dispatchers.IO) {
        if (onlineContent.url.isEmpty()) return@withContext ContentStatus.UNDOWNLOADABLE
        if (onlineContent.status == StatusContent.IGNORED) return@withContext ContentStatus.UNDOWNLOADABLE
        currentContent = null
        Timber.i("Processing ${onlineContent.site.name} Content @ ${onlineContent.url} (cover ${onlineContent.coverImageUrl}) $quickDownload")
        val searchUrl =
            if (getStartSite().hasCoverBasedPageUpdates) onlineContent.coverImageUrl else ""
        // TODO manage DB calls concurrency to avoid getting read transaction conflicts
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            val contentDB =
                dao.selectContentByUrlOrCover(
                    onlineContent.site,
                    onlineContent.url,
                    searchUrl
                )
            val isInCollection = contentDB != null && isInLibrary(contentDB.status)
            val isInQueue = contentDB != null && isInQueue(contentDB.status)
            if (!isInCollection && !isInQueue) {
                if (Settings.downloadDuplicateAsk && onlineContent.coverImageUrl.isNotEmpty()) {
                    // Index the content's cover picture
                    var pHash = Long.MIN_VALUE
                    try {
                        val requestHeadersList: List<Pair<String, String>> = ArrayList()
                        val downloadParams =
                            parseDownloadParams(onlineContent.downloadParams).toMutableMap()
                        downloadParams[HEADER_COOKIE_KEY] =
                            getCookies(onlineContent.coverImageUrl)
                        downloadParams[HEADER_REFERER_KEY] = onlineContent.site.url
                        getOnlineResourceFast(
                            fixUrl(onlineContent.coverImageUrl, onlineContent.site.url),
                            requestHeadersList,
                            getStartSite().useMobileAgent,
                            getStartSite().useHentoidAgent,
                            getStartSite().useWebviewAgent
                        ).use { onlineCover ->
                            val coverBody = onlineCover.body
                            val bodyStream = coverBody.byteStream()
                            val b = getCoverBitmapFromStream(bodyStream)
                            pHash = calcPhash(getHashEngine(), b)
                        }
                    } catch (e: IOException) {
                        Timber.w(e)
                    } catch (e: IllegalArgumentException) {
                        Timber.w(e)
                    }
                    // Look for duplicates
                    try {
                        val duplicateResult = findDuplicate(
                            this@BaseBrowserActivity,
                            onlineContent,
                            Settings.duplicateBrowserUseTitle,
                            Settings.duplicateBrowserUseArtist,
                            Settings.duplicateBrowserUseSameLanguage,
                            Settings.duplicateBrowserUseCover,
                            Settings.duplicateBrowserSensitivity,
                            pHash,
                            dao
                        )
                        if (duplicateResult != null) {
                            duplicateId = duplicateResult.first.id
                            duplicateSimilarity = duplicateResult.second
                            // Content ID of the duplicate candidate of the currently viewed Content
                            val duplicateSameSite = duplicateResult.first.site == onlineContent.site
                            // Same site and similar => enable download button by default, but look for extra pics just in case
                            if (duplicateSameSite && Settings.downloadPlusDuplicateTry && !quickDownload)
                                searchForExtraImages(duplicateResult.first, onlineContent)
                        }
                    } catch (e: Exception) {
                        Timber.w(e)
                    }
                }
                if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB
                    onlineContent.status = StatusContent.SAVED
                    addContent(this@BaseBrowserActivity, dao, onlineContent)
                } else {
                    onlineContent.id = contentDB.id
                    currentContent = contentDB
                }
            } else {
                onlineContent.id = contentDB.id
                currentContent = contentDB
            }
            if (null == currentContent) currentContent = onlineContent
            if (isInCollection) {
                if (!quickDownload) searchForExtraImages(contentDB, onlineContent)
                return@withContext ContentStatus.IN_COLLECTION
            }
            return@withContext if (isInQueue) ContentStatus.IN_QUEUE else ContentStatus.UNKNOWN
        } finally {
            dao.cleanup()
        }
    }

    override fun onContentReady(content: Content, quickDownload: Boolean) {
        // TODO Cancel whichever process was happening before
        if (Settings.isBrowserMode) return

        lifecycleScope.launch {
            try {
                val status = processContent(content, quickDownload)
                onContentProcessed(content, status, quickDownload)
            } catch (t: Throwable) {
                Timber.e(t)
                onContentProcessed(content, ContentStatus.UNKNOWN, false)
            }
        }
    }

    private fun onContentProcessed(
        content: Content,
        status: ContentStatus,
        quickDownload: Boolean
    ) {
        binding?.quickDlFeedback?.visibility = View.INVISIBLE
        when (status) {
            ContentStatus.UNDOWNLOADABLE -> onResultFailed()
            ContentStatus.UNKNOWN -> {
                if (quickDownload) {
                    if (duplicateId > -1 && Settings.downloadDuplicateAsk) DuplicateDialogFragment.invoke(
                        this,
                        duplicateId,
                        content.qtyPages,
                        duplicateSimilarity,
                        false
                    ) else processDownload(content, quickDownload = true)
                } else lifecycleScope.launch { setActionMode(ActionMode.DOWNLOAD) }
            }

            ContentStatus.IN_COLLECTION -> {
                if (quickDownload) toast(R.string.already_downloaded)
                lifecycleScope.launch { setActionMode(ActionMode.READ) }
            }

            ContentStatus.IN_QUEUE -> {
                if (quickDownload) toast(R.string.already_queued)
                lifecycleScope.launch { setActionMode(ActionMode.VIEW_QUEUE) }
            }
        }
        val dao: CollectionDAO = ObjectBoxDAO()
        blockedTags = getBlockedTags(content.id, dao).toMutableList()
        dao.cleanup()
    }

    override fun onNoResult() {
        Timber.v("onNoResult")
        lifecycleScope.launch { setActionMode(null) }
    }

    override fun onResultFailed() {
        runOnUiThread { toast(R.string.web_unparsable) }
    }

    private fun searchForExtraImages(storedContent: Content, onlineContent: Content) {
        // TODO cancel previous operation
        lifecycleScope.launch {
            try {
                val imageFiles = withContext(Dispatchers.IO) {
                    doSearchForExtraImages(storedContent, onlineContent)
                }
                onSearchForExtraImagesSuccess(storedContent, onlineContent, imageFiles)
            } catch (t: Throwable) {
                Timber.w(t)
            }
        }
    }

    @Throws(Exception::class)
    private fun doSearchForExtraImages(
        storedContent: Content,
        onlineContent: Content
    ): List<ImageFile> {
        val result = emptyList<ImageFile>()
        val parser = ContentParserFactory.getImageListParser(onlineContent)
        // Call the parser to retrieve all the pages
        // Progress bar on browser UI is refreshed through onDownloadPreparationEvent
        val onlineImgs: List<ImageFile>
        try {
            onlineImgs = parser.parseImageList(onlineContent, storedContent)
        } finally {
            parser.clear()
        }
        if (onlineImgs.isEmpty()) return result

        var maxStoredImageOrder = 0
        val opt = storedContent.imageFiles
            .filter { i: ImageFile -> isInLibrary(i.status) }
            .maxOfOrNull { img -> img.order }

        if (opt != null) maxStoredImageOrder = opt
        val maxStoredImageOrderFinal = maxStoredImageOrder

        // Attach chapters to books downloaded before chapters were implemented
        var maxOnlineImageOrder = 0
        var minOnlineImageOrder = Int.MAX_VALUE
        val positionMap: MutableMap<Int, Chapter?> = HashMap()
        for (img in onlineImgs) {
            maxOnlineImageOrder = maxOnlineImageOrder.coerceAtLeast(img.order)
            minOnlineImageOrder = minOnlineImageOrder.coerceAtMost(img.order)
            if (null != img.linkedChapter) positionMap[img.order] = img.linkedChapter
        }

        // Attach chapters to stored images if they don't have any (old downloads made with versions of the app that didn't detect chapters)
        val storedChapters: List<Chapter> = storedContent.chapters
        if (positionMap.isNotEmpty() && minOnlineImageOrder < maxStoredImageOrder && storedChapters.isEmpty()) {
            val storedImages = storedContent.imageList
            for (img in storedImages) {
                if (null == img.linkedChapter) {
                    val targetChapter = positionMap[img.order]
                    if (targetChapter != null) img.setChapter(targetChapter)
                }
            }
            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                dao.insertImageFiles(storedImages)
            } finally {
                dao.cleanup()
            }
        }

        // Online book has more pictures than stored book -> that's what we're looking for
        return if (maxOnlineImageOrder > maxStoredImageOrder) {
            onlineImgs.filter { it.order > maxStoredImageOrderFinal }.distinct()
        } else result
    }

    private fun onSearchForExtraImagesSuccess(
        storedContent: Content,
        onlineContent: Content,
        additionalImages: List<ImageFile>
    ) {
        binding?.apply {
            progressBar.progress = progressBar.max
            progressBar.visibility = View.VISIBLE
            progressBar.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(baseContext, R.color.green)
            )
        }
        if (null == currentContent || additionalImages.isEmpty()) return

        if (currentContent!!.url.equals(
                onlineContent.url,
                ignoreCase = true
            ) || duplicateId == storedContent.id
        ) { // User hasn't left the book page since
            // Retrieve the URLs of stored pages
            val storedUrls: MutableSet<String> = HashSet()
            storedContent.imageFiles.let { imgs ->
                storedUrls.addAll(
                    imgs
                        .filter { isInLibrary(it.status) }.map { it.url }.toList()
                )
            }
            // Memorize the title of the online content (to update title of stored book later)
            onlineContentTitle = onlineContent.title

            // Display the "download more" button only if extra images URLs aren't duplicates
            val additionalNonDownloadedImages =
                additionalImages.filterNot { storedUrls.contains(it.url) }
            if (additionalNonDownloadedImages.isNotEmpty()) {
                extraImages = additionalNonDownloadedImages.toMutableList()
                lifecycleScope.launch { setActionMode(ActionMode.DOWNLOAD_PLUS) }
                binding?.apply {
                    actionBtnBadge.text = String.format(
                        Locale.ENGLISH,
                        "%d",
                        additionalNonDownloadedImages.size
                    )
                    actionBtnBadge.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateDownloadedBooksUrls() {
        synchronized(downloadedBooksUrls) {
            downloadedBooksUrls.clear()
            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                downloadedBooksUrls.addAll(
                    dao.selectAllSourceUrls(getStartSite())
                        .map { simplifyUrl(it) }
                        .filterNot { it.isEmpty() }
                )
            } finally {
                dao.cleanup()
            }
        }
    }

    private fun clearDownloadedBooksUrls() {
        downloadedBooksUrls.clear()
    }

    private fun updateMergedBooksUrls() {
        synchronized(mergedBooksUrls) {
            mergedBooksUrls.clear()
            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                mergedBooksUrls.addAll(
                    dao.selectAllMergedUrls(getStartSite())
                        .asSequence()
                        .map { it.replace(getStartSite().url, "") }
                        .map {
                            it.replace(
                                GALLERY_REGEX,
                                ""
                            )
                        } //each sites "gallery" path
                        .map { simplifyUrl(it) }
                        .filterNot { it.isEmpty() }
                )
            } finally {
                dao.cleanup()
            }
        }
    }

    private fun clearMergedBooksUrls() {
        mergedBooksUrls.clear()
    }

    private fun updateQueuedBooksUrls() {
        synchronized(queuedBooksUrls) {
            queuedBooksUrls.clear()
            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                queuedBooksUrls.addAll(
                    dao.selectQueueUrls(getStartSite())
                        .map { simplifyUrl(it) }
                        .filterNot { it.isEmpty() }
                )
            } finally {
                dao.cleanup()
            }
        }
    }

    private fun clearQueueBooksUrls() {
        queuedBooksUrls.clear()
    }

    private fun updatePrefBlockedTags() {
        internalPrefBlockedTags.clear()
        internalPrefBlockedTags.addAll(Settings.blockedTags)
    }

    private fun clearPrefBlockedTags() {
        internalPrefBlockedTags.clear()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    override fun onCommunicationEvent(event: CommunicationEvent) {
        super.onCommunicationEvent(event)
        if (CommunicationEvent.Type.CLOSE_DRAWER == event.type) {
            closeNavigationDrawer()
            closeBookmarksDrawer()
        }
    }

    fun closeNavigationDrawer() {
        binding?.drawerLayout?.closeDrawer(GravityCompat.START)
    }

    fun closeBookmarksDrawer() {
        binding?.drawerLayout?.closeDrawer(GravityCompat.END)
    }

    /**
     * Listener for the events of the download engine
     * Used to switch the action button to Read when the download of the currently viewed is completed
     *
     * @param event Event fired by the download engine
     */
    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        if (event.eventType === DownloadEvent.Type.EV_COMPLETE) {
            if (webClient.isMarkDownloaded()) updateDownloadedBooksUrls()
            if (webClient.isMarkQueued()) updateQueuedBooksUrls()
            if (event.content != null && event.content == currentContent && event.content.status == StatusContent.DOWNLOADED) {
                lifecycleScope.launch { setActionMode(ActionMode.READ) }
            }
        }
    }

    fun tooltip(@StringRes resource: Int, always: Boolean) {
        binding?.apply {
            this@BaseBrowserActivity.showTooltip(
                resource,
                ArrowOrientation.BOTTOM,
                bottomNavigation,
                this@BaseBrowserActivity,
                always
            )
        }
    }

    override fun onDownloadDuplicate(actionMode: DuplicateDialogFragment.ActionMode) {
        currentContent?.let { cc ->
            processDownload(
                cc,
                isDownloadPlus = actionMode == DuplicateDialogFragment.ActionMode.DOWNLOAD_PLUS,
                isReplaceDuplicate = actionMode == DuplicateDialogFragment.ActionMode.REPLACE
            )
        }
    }


    /**
     * Indicate if the browser's back list contains a book gallery
     * Used to determine the display of the "back to latest gallery" button
     *
     * NB : Internal implementation of WebBackForwardList seems to limit its size to 50 items,
     * which causes deceptive behaviour when the previous gallery page is "far", navigation-wise
     * @param backForwardList Back list to examine
     * @return Index of the latest book gallery in the list; -1 if none has been detected
     */
    private fun backListContainsGallery(backForwardList: WebBackForwardList): Int {
        for (i in backForwardList.currentIndex - 1 downTo 0) {
            val item = backForwardList.getItemAtIndex(i)
            if (webClient.isGalleryPage(item.url)) return i
        }
        return -1
    }

    /**
     * Format the message to display for the given source alert
     *
     * @param theAlert Source alert
     * @return Message to be displayed for the user for the given source alert
     */
    private fun formatAlertMessage(theAlert: UpdateInfo.SourceAlert): String {
        // Main message body
        var result = when (theAlert.getStatus()) {
            AlertStatus.ORANGE -> resources.getString(R.string.alert_orange)
            AlertStatus.RED -> resources.getString(R.string.alert_red)
            AlertStatus.GREY -> resources.getString(R.string.alert_grey)
            AlertStatus.BLACK -> resources.getString(R.string.alert_black)
            else -> resources.getString(R.string.alert_orange)
        }

        // End of message
        result =
            if (theAlert.getFixedByBuild() < Int.MAX_VALUE) result.replace(
                "%s",
                resources.getString(R.string.alert_fix_available)
            ) else result.replace("%s", resources.getString(R.string.alert_wip))
        return result
    }

    /**
     * Show the browser settings dialog
     */
    private fun onSettingsClick() {
        val intent = Intent(this, SettingsActivity::class.java)
        val settingsBundle = SettingsBundle()
        settingsBundle.isBrowserSettings = true
        settingsBundle.site = getStartSite().code
        intent.putExtras(settingsBundle.bundle)
        startActivity(intent)
    }

    override val allSiteUrls: List<String>
        get() = downloadedBooksUrls.toMutableList() // Work on a copy to avoid any thread-synch issue
    override val allMergedBooksUrls: List<String>
        get() = mergedBooksUrls.toMutableList()
    override val allQueuedBooksUrls: List<String>
        get() = queuedBooksUrls.toMutableList()
    override val prefBlockedTags: List<String>
        get() = internalPrefBlockedTags.toMutableList()
    override val customCss: String
        get() = computeCustomCss()
    override val alertStatus: AlertStatus
        get() = alert?.getStatus() ?: AlertStatus.NONE
    override val scope: LifecycleCoroutineScope
        get() = lifecycleScope

    private fun computeCustomCss(): String {
        if (null == internalCustomCss) {
            val sb = StringBuilder()
            if (Settings.isBrowserMarkDownloaded || Settings.isBrowserMarkMerged || Settings.isBrowserMarkQueued || Settings.isBrowserMarkBlockedTags) getAssetAsString(
                assets, "downloaded.css", sb
            )
            if (getStartSite() == Site.NHENTAI && Settings.isBrowserNhentaiInvisibleBlacklist) getAssetAsString(
                assets, "nhentai_invisible_blacklist.css", sb
            )
            if (getStartSite() == Site.IMHENTAI) getAssetAsString(
                assets, "imhentai.css", sb
            )
            if (getStartSite() == Site.EROMANGA) getAssetAsString(
                assets, "eromanga-sora.css", sb
            )
            if (getStartSite() == Site.HENTAIFOX) getAssetAsString(
                assets, "hentaifox.css", sb
            )
            if (getStartSite() == Site.NHENTAI) getAssetAsString(
                assets, "nhentai.css", sb
            )
            if (getStartSite() == Site.KSK) getAssetAsString(
                assets, "ksk.css", sb
            )
            if (getStartSite() == Site.PIXIV && Settings.isBrowserAugmented(getStartSite()))
                getAssetAsString(assets, "pixiv.css", sb)
            internalCustomCss = sb.toString()
        }
        return internalCustomCss!!
    }

    fun browserFetch(url: String, callback: Consumer<String>? = null) {
        fetchResponseCallback = callback
        webView.evaluateJavascript("fetch(\"$url\")", null)
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param key   Key that has been changed
     */
    private fun onSharedPreferenceChanged(key: String) {
        var reload = false
        Timber.v("onSharedPreferenceChanged $key")
        if (Settings.Key.BROWSER_DL_ACTION == key) {
            downloadIcon =
                if (Settings.getBrowserDlAction() == DownloadMode.STREAM) R.drawable.selector_download_stream_action else R.drawable.selector_download_action
            lifecycleScope.launch { setActionMode(actionButtonMode) }
        } else if (Settings.Key.BROWSER_MARK_DOWNLOADED == key) {
            internalCustomCss = null
            webClient.setMarkDownloaded(Settings.isBrowserMarkDownloaded)
            if (webClient.isMarkDownloaded()) updateDownloadedBooksUrls() else clearDownloadedBooksUrls()
            reload = true
        } else if (Settings.Key.BROWSER_MARK_MERGED == key) {
            internalCustomCss = null
            webClient.setMarkMerged(Settings.isBrowserMarkMerged)
            if (webClient.isMarkMerged()) updateMergedBooksUrls() else clearMergedBooksUrls()
            reload = true
        } else if (Settings.Key.BROWSER_MARK_QUEUED == key) {
            internalCustomCss = null
            webClient.setMarkQueued(Settings.isBrowserMarkQueued)
            if (webClient.isMarkQueued()) updateQueuedBooksUrls() else clearQueueBooksUrls()
            reload = true
        } else if (Settings.Key.BROWSER_MARK_BLOCKED == key) {
            internalCustomCss = null
            webClient.setMarkBlockedTags(Settings.isBrowserMarkBlockedTags)
            if (webClient.isMarkBlockedTags()) updatePrefBlockedTags() else clearPrefBlockedTags()
            reload = true
        } else if (Settings.Key.DL_BLOCKED_TAGS == key) {
            updatePrefBlockedTags()
            reload = true
        } else if (Settings.Key.BROWSER_NHENTAI_INVISIBLE_BLACKLIST == key) {
            internalCustomCss = null
            reload = true
        } else if (Settings.Key.BROWSER_DNS_OVER_HTTPS == key) {
            webClient.setDnsOverHttpsEnabled(Settings.dnsOverHttps > -1)
            reload = true
        } else if (Settings.Key.BROWSER_PROXY == key) {
            webClient.setProxyEnabled(Settings.proxy.isNotEmpty())
            reload = true
        } else if (Settings.Key.BROWSER_QUICK_DL == key) {
            if (Settings.isBrowserQuickDl) webView.setOnLongTapListener { x: Int, y: Int ->
                onLongTap(x, y)
            } else webView.setOnLongTapListener(null)
        } else if (Settings.Key.BROWSER_QUICK_DL_THRESHOLD == key) {
            webView.setLongClickThreshold(Settings.browserQuickDlThreshold)
        } else if (key.startsWith(Settings.Key.WEB_ADBLOCKER)) {
            val newVal = Settings.isAdBlockerOn(getStartSite())
            if (newVal && !Settings.isBrowserAugmented(getStartSite()))
                Settings.setBrowserAugmented(getStartSite(), true)
            updateAdblockButton(newVal)
            webClient.adBlocker.setActive(newVal)
            reload = true
        } else if (Settings.Key.WEB_LOCK_FAVS_PANEL == key) {
            binding?.drawerLayout?.setDrawerLockMode(
                if (Settings.isBrowserLockFavPanel) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED,
                GravityCompat.END
            )
        }
        if (reload && !webClient.isLoading()) webView.reload()
    }

    // References :
    // https://stackoverflow.com/a/64961272/8374722
    // https://stackoverflow.com/questions/3941969/android-intercept-ajax-call-from-webview/5742194
    class FetchHandler(private val handler: BiConsumer<String, String>) {
        @JavascriptInterface
        @Suppress("unused")
        fun onFetchCall(url: String, body: String?) {
            Timber.d("fetch Begin %s : %s", url, body)
            handler.invoke(url, body ?: "")
        }
    }

    class FetchResponseHandler(private val handler: Consumer<String>) {
        @JavascriptInterface
        @Suppress("unused")
        fun onFetchCall(url: String, body: String?, responseBody: String) {
            Timber.d("fetch response $url $body $responseBody")
            handler.invoke(responseBody)
        }
    }

    // References :
    // https://medium.com/@madmuc/intercept-all-network-traffic-in-webkit-on-android-9c56c9262c85
    class XhrHandler(private val handler: BiConsumer<String, String>) {
        @JavascriptInterface
        @Suppress("unused", "UNUSED_PARAMETER")
        fun onXhrCall(method: String, url: String, body: String?) {
            Timber.d("XHR Begin %s : %s", url, body)
            handler.invoke(url, body ?: "")
        }
    }
}