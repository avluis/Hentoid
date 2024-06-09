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
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.AboutActivity
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.MissingWebViewActivity
import me.devsaki.hentoid.activities.PrefsActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Content.DownloadMode
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.databinding.ActivityBaseWebBinding
import me.devsaki.hentoid.enums.AlertStatus
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.events.DownloadPreparationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.fragments.web.BookmarksDialogFragment
import me.devsaki.hentoid.fragments.web.DuplicateDialogFragment
import me.devsaki.hentoid.fragments.web.UrlDialogFragment
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.ui.invokeNumberInputDialog
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Preferences.Constant
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.calcPhash
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.getAssetAsString
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.findDuplicate
import me.devsaki.hentoid.util.getBlockedTags
import me.devsaki.hentoid.util.getCoverBitmapFromStream
import me.devsaki.hentoid.util.getHashEngine
import me.devsaki.hentoid.util.getThemedColor
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
import me.devsaki.hentoid.util.showTooltip
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.views.NestedScrollWebView
import me.devsaki.hentoid.widget.AddQueueMenu.Companion.show
import me.devsaki.hentoid.widget.DownloadModeMenu.Companion.show
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
abstract class BaseWebActivity : BaseActivity(), CustomWebViewClient.CustomWebActivity,
    BookmarksDialogFragment.Parent, DuplicateDialogFragment.Parent {

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

    companion object {
        const val SIMILARITY_MIN_THRESHOLD = 0.85f
    }


    // === NUTS AND BOLTS
    private lateinit var webClient: CustomWebViewClient

    private var callback: OnBackPressedCallback? = null

    // Database
    private lateinit var dao: CollectionDAO

    private val listener =
        OnSharedPreferenceChangeListener { _, key: String? ->
            onSharedPreferenceChanged(key)
        }

    // === UI
    private var binding: ActivityBaseWebBinding? = null

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

    // List of tags of Preference-browser-blocked tags
    private var m_prefBlockedTags: MutableList<String> = ArrayList()

    // === OTHER VARIABLES
    // Indicates which mode the download button is in
    protected var actionButtonMode: ActionMode? = null

    // Indicates which mode the seek button is in
    private var seekButtonMode: SeekMode? = null

    // Alert to be displayed
    private var alert: UpdateInfo.SourceAlert? = null

    // Handler for fetch interceptor
    protected var fetchHandler: BiConsumer<String, String>? = null
    protected var xhrHandler: BiConsumer<String, String>? = null
    private var jsInterceptorScript: String? = null
    private var m_customCss: String? = null


    protected abstract fun createWebClient(): CustomWebViewClient

    abstract fun getStartSite(): Site


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBaseWebBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            startActivity(Intent(this, MissingWebViewActivity::class.java))
            return
        }
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        dao = ObjectBoxDAO()
        Preferences.registerPrefsChangedListener(listener)
        if (Preferences.isBrowserMarkDownloaded()) updateDownloadedBooksUrls()
        if (Preferences.isBrowserMarkMerged()) updateMergedBooksUrls()
        if (Preferences.isBrowserMarkBlockedTags()) updatePrefBlockedTags()
        Timber.d("Loading site: %s", getStartSite())

        // Toolbar
        // Top toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        Helper.tryShowMenuIcons(this, toolbar.menu)
        toolbar.setOnMenuItemClickListener { item ->
            this.onMenuItemSelected(item)
        }
        toolbar.title = getStartSite().description
        toolbar.setOnClickListener { loadUrl(getStartSite().url) }

        refreshStopMenu = toolbar.menu.findItem(R.id.web_menu_refresh_stop)
        bookmarkMenu = toolbar.menu.findItem(R.id.web_menu_bookmark)
        adblockMenu = toolbar.menu.findItem(R.id.web_menu_adblocker)
        binding?.apply {
            this@BaseWebActivity.languageFilterButton = languageFilterButton
            bottomNavigation.setOnMenuItemClickListener { item ->
                this@BaseWebActivity.onMenuItemSelected(item)
            }
            menuHome.setOnClickListener { goHome() }
            menuSeek.setOnClickListener { onSeekClick() }
            menuBack.setOnClickListener { onBackClick() }
            menuForward.setOnClickListener { onForwardClick() }
            actionButton.setOnClickListener { onActionClick() }
        }

        // Webview
        initWebview()
        initSwipeLayout()
        webView.loadUrl(getStartUrl())
        if (!Preferences.getRecentVisibility()) {
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
            if (Preferences.getBrowserDlAction() == DownloadMode.STREAM) R.drawable.selector_download_stream_action else R.drawable.selector_download_action
        if (Preferences.isBrowserMode()) downloadIcon = R.drawable.ic_forbidden_disabled
        binding?.actionButton?.setImageDrawable(ContextCompat.getDrawable(this, downloadIcon))
        displayTopAlertBanner()
        updateAdblockButton(Settings.isAdBlockerOn)

        addCustomBackControl()
    }

    private fun addCustomBackControl() {
        callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!webView.canGoBack()) {
                    callback?.remove()
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback!!)
    }

    /**
     * Determine the URL the browser will load at startup
     * - Either an URL specifically given to the activity (e.g. "view source" action)
     * - Or the last viewed page, if the option is enabled
     * - If neither of the previous cases, the default URL of the site
     *
     * @return URL to load at startup
     */
    private fun getStartUrl(): String {
        // Priority 1 : URL specifically given to the activity (e.g. "view source" action)
        if (intent.extras != null) {
            val bundle = BaseWebActivityBundle(intent.extras!!)
            val intentUrl = StringHelper.protect(bundle.url)
            if (intentUrl.isNotEmpty()) return intentUrl
        }

        // Priority 2 : Last viewed position, if option enabled
        if (Preferences.isBrowserResumeLast()) {
            val siteHistory = dao.selectHistory(getStartSite())
            if (!siteHistory.url.isNullOrEmpty()) return siteHistory.url
        }

        // Priority 3 : Homepage, if manually set through bookmarks
        val welcomePage = dao.selectHomepage(getStartSite())
        return welcomePage?.url ?: getStartSite().url

        // Default site URL
    }

    @SuppressLint("NonConstantResourceId")
    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.web_menu_bookmark -> onBookmarkClick()
            R.id.web_menu_refresh_stop -> onRefreshStopClick()
            R.id.web_menu_settings -> onSettingsClick()
            R.id.web_menu_adblocker -> onAdblockClick()
            R.id.web_menu_url -> onManageLinkClick()
            R.id.web_menu_about -> onAboutClick()
            else -> {
                return false
            }
        }
        return true
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun onUpdateEvent(event: UpdateEvent) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        val url = webView.url
        if (url != null) {
            val bundle = BaseWebActivityBundle()
            if (WebkitPackageHelper.getWebViewAvailable()) bundle.url = url
            outState.putAll(bundle.bundle)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        // NB : This doesn't restore the browsing history, but WebView.saveState/restoreState
        // doesn't work that well (bugged when using back/forward commands). A valid solution still has to be found
        val url = BaseWebActivityBundle(savedInstanceState).url
        if (url.isNotEmpty()) webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            startActivity(Intent(this, MissingWebViewActivity::class.java))
            return
        }
        checkPermissions()
        val url = webView.url
        Timber.i(
            ">> WebActivity resume : %s %s %s",
            url,
            currentContent != null,
            if (currentContent != null) currentContent!!.title else ""
        )
        if (currentContent != null && url != null && createWebClient().isGalleryPage(url)) {
            // TODO Cancel whichever process was happening before
            lifecycleScope.launch {
                try {
                    val status = withContext(Dispatchers.IO) {
                        processContent(currentContent!!, false)
                    }
                    onContentProcessed(status, false)
                } catch (t: Throwable) {
                    Timber.e(t)
                    onContentProcessed(ContentStatus.UNKNOWN, false)
                }
            }
        }
    }

    override fun onStop() {
        if (WebkitPackageHelper.getWebViewAvailable() && webView.url != null)
            dao.insertSiteHistory(getStartSite(), webView.url!!)
        super.onStop()
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
        Preferences.unregisterPrefsChangedListener(listener)

        // Cancel any previous extra page load
        EventBus.getDefault().post(
            DownloadCommandEvent(
                DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT,
                currentContent
            )
        )
        dao.cleanup()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        binding = null
        super.onDestroy()
    }

    // Make sure permissions are set at resume time; if not, warn the user
    private fun checkPermissions() {
        if (Preferences.isBrowserMode()) return
        if (!this.requestExternalStorageReadWritePermission(RQST_STORAGE_PERMISSION))
            toast(R.string.web_storage_permission_denied)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebview() {
        webView = try {
            NestedScrollWebView(this)
        } catch (e: NotFoundException) {
            // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
            // Creating with the application Context fixes this, but is not generally recommended for view creation
            NestedScrollWebView(Helper.getFixedContext(this))
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
        val bWebViewOverview = Preferences.getWebViewOverview()
        val webViewInitialZoom = Preferences.getWebViewInitialZoom()
        if (bWebViewOverview) {
            webView.settings.loadWithOverviewMode = false
            webView.setInitialScale(webViewInitialZoom)
            Timber.d("WebView Initial Scale: %s%%", webViewInitialZoom)
        } else {
            webView.setInitialScale(Preferences.Default.WEBVIEW_INITIAL_ZOOM)
            webView.settings.loadWithOverviewMode = true
        }
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        webClient = createWebClient()
        webView.webViewClient = webClient

        // Download immediately on long click on a link / image link
        if (Preferences.isBrowserQuickDl()) {
            webView.setOnLongTapListener { x: Int, y: Int ->
                onLongTap(x, y)
            }
            webView.setLongClickThreshold(Preferences.getBrowserQuickDlThreshold())
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
        xhrHandler?.let { webView.addJavascriptInterface(XhrHandler(it), "xhrHandler") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            webSettings.isAlgorithmicDarkeningAllowed =
                (!Settings.isBrowserForceLightMode && Preferences.getColorTheme() != Constant.COLOR_THEME_LIGHT)
        }
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

    private fun onLongTap(x: Int, y: Int) {
        if (Preferences.isBrowserMode()) return
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
                Helper.setMargins(
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

            // Run on a new thread to avoid crashes
            lifecycleScope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        webClient.parseResponse(
                            url, null,
                            analyzeForDownload = true,
                            quickDownload = true
                        )
                    }
                    if (null == res) {
                        binding?.quickDlFeedback?.visibility = View.INVISIBLE
                    } else {
                        binding?.quickDlFeedback?.setIndicatorColor(
                            baseContext.getThemedColor(R.color.secondary_light)
                        )
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    override fun onPageStarted(
        url: String,
        isGalleryPage: Boolean,
        isHtmlLoaded: Boolean,
        isBookmarkable: Boolean
    ) {
        refreshStopMenu?.setIcon(R.drawable.ic_close)
        binding?.progressBar?.visibility = View.GONE
        if (!isHtmlLoaded) disableActions()

        // Activate fetch handler
        if (fetchHandler != null) {
            if (null == jsInterceptorScript) jsInterceptorScript =
                webClient.getJsScript(this, "fetch_override.js", null)
            webView.loadUrl(jsInterceptorScript!!)
        }
        // Activate XHR handler
        if (xhrHandler != null) {
            if (null == jsInterceptorScript) jsInterceptorScript =
                webClient.getJsScript(this, "xhr_override.js", null)
            webView.loadUrl(jsInterceptorScript!!)
        }

        // Display download button tooltip if a book page has been reached
        if (isGalleryPage && !Preferences.isBrowserMode()) tooltip(
            R.string.help_web_download,
            false
        )
        // Update bookmark button
        if (isBookmarkable) {
            val bookmarks = dao.selectBookmarks(getStartSite())
            val currentBookmark = bookmarks.firstOrNull { b ->
                SiteBookmark.urlsAreSame(b.url, url)
            }

            updateBookmarkButton(currentBookmark != null)
        }
    }

    // WARNING : This method may not be called from the UI thread
    override fun onGalleryPageStarted() {
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
        runOnUiThread {
            binding?.apply {
                actionButton.setImageDrawable(ContextCompat.getDrawable(baseContext, downloadIcon))
                actionButton.visibility = View.INVISIBLE
                actionBtnBadge.visibility = View.INVISIBLE
            }
        }
    }

    override fun onPageFinished(url: String, isResultsPage: Boolean, isGalleryPage: Boolean) {
        refreshNavigationMenu(isResultsPage)
        refreshStopMenu?.setIcon(R.drawable.ic_action_refresh)

        // Manage bottom alert banner visibility
        if (isGalleryPage) displayBottomAlertBanner(blockedTags) // Called here to be sure it is displayed on the gallery page
        else onBottomAlertCloseClick()
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
            isResultsPage || backListContainsGallery(
                webView.copyBackForwardList()
            ) > -1
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
            invokeNumberInputDialog(this, R.string.goto_page) { i -> goToPage(i) }
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
        BookmarksDialogFragment.invoke(
            this,
            getStartSite(),
            StringHelper.protect(webView.title),
            StringHelper.protect(webView.url)
        )
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
        Settings.isAdBlockerOn = !Settings.isAdBlockerOn
    }

    /**
     * Handler for the "Manage link" button
     */
    private fun onManageLinkClick() {
        val url = StringHelper.protect(webView.url)
        if (Helper.copyPlainTextToClipboard(this, url))
            toast(R.string.web_url_clipboard)
        UrlDialogFragment.invoke(this, url)
    }

    /**
     * Handler for the "Home" navigation button
     */
    @Suppress("DEPRECATION")
    private fun goHome() {
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

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun updateBookmarkButton(newValue: Boolean) {
        if (newValue) bookmarkMenu?.setIcon(R.drawable.ic_bookmark_full)
        else bookmarkMenu?.setIcon(R.drawable.ic_bookmark)
    }

    private fun updateAdblockButton(targetValue: Boolean) {
        val targetTxt = if (targetValue) R.string.web_adblocker_on else R.string.web_adblocker_off
        val targetIco = if (targetValue) R.drawable.ic_shield_on else R.drawable.ic_shield_off
        adblockMenu?.icon = ContextCompat.getDrawable(this, targetIco)
        adblockMenu?.title = resources.getString(targetTxt)
    }

    /**
     * Listener for the Action button : download content, view queue or read content
     */
    protected open fun onActionClick() {
        if (null == currentContent) return
        val needsDuplicateAlert =
            Preferences.isDownloadDuplicateAsk() && duplicateSimilarity >= SIMILARITY_MIN_THRESHOLD
        when (actionButtonMode) {
            ActionMode.DOWNLOAD -> {
                if (needsDuplicateAlert) DuplicateDialogFragment.invoke(
                    this,
                    duplicateId,
                    currentContent!!.qtyPages,
                    duplicateSimilarity,
                    false
                ) else processDownload(
                    quickDownload = false,
                    isDownloadPlus = false,
                    isReplaceDuplicate = false
                )
            }

            ActionMode.DOWNLOAD_PLUS -> {
                if (needsDuplicateAlert) DuplicateDialogFragment.invoke(
                    this,
                    duplicateId,
                    currentContent!!.qtyPages,
                    duplicateSimilarity,
                    true
                ) else processDownload(
                    quickDownload = false,
                    isDownloadPlus = true,
                    isReplaceDuplicate = false
                )
            }

            ActionMode.VIEW_QUEUE -> goToQueue()
            ActionMode.READ -> {
                val searchUrl =
                    if (getStartSite().hasCoverBasedPageUpdates()) currentContent!!.coverImageUrl else ""
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
                    binding?.apply {
                        actionButton.visibility = View.INVISIBLE
                        actionBtnBadge.visibility = View.INVISIBLE
                    }
                }
            }

            else -> {
                // Nothing
            }
        }
    }

    private fun disableActions() {
        val b: ActivityBaseWebBinding? = binding
        if (b != null) {
            b.actionButton.setImageDrawable(ContextCompat.getDrawable(this, downloadIcon))
            b.actionButton.visibility = View.INVISIBLE
            b.actionBtnBadge.visibility = View.INVISIBLE
        }
    }

    /**
     * Switch the action button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private fun setActionMode(mode: ActionMode?) {
        val b: ActivityBaseWebBinding? = binding
        if (Preferences.isBrowserMode() && b != null) {
            b.actionButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_forbidden_disabled
                )
            )
            b.actionButton.visibility = View.INVISIBLE
            b.actionBtnBadge.visibility = View.INVISIBLE
            return
        }
        @DrawableRes var resId: Int = R.drawable.ic_info
        if (ActionMode.DOWNLOAD == mode || ActionMode.DOWNLOAD_PLUS == mode) {
            resId = downloadIcon
        } else if (ActionMode.VIEW_QUEUE == mode) {
            resId = R.drawable.ic_action_queue
        } else if (ActionMode.READ == mode) {
            resId = R.drawable.ic_action_play
        }
        actionButtonMode = mode
        if (b != null) {
            b.actionButton.setImageDrawable(ContextCompat.getDrawable(this, resId))
            b.actionButton.visibility = View.VISIBLE
            // It will become visible whenever the count of extra pages is known
            if (ActionMode.DOWNLOAD_PLUS != mode) b.actionBtnBadge.visibility = View.INVISIBLE
        }
    }

    /**
     * Switch the seek button to either of the available modes
     *
     * @param mode Mode to switch to
     */
    private fun changeSeekMode(mode: SeekMode, enabled: Boolean) {
        @DrawableRes var resId: Int = R.drawable.selector_back_gallery
        if (SeekMode.PAGE == mode) resId = R.drawable.selector_page_seek
        seekButtonMode = mode
        val b: ActivityBaseWebBinding? = binding
        if (b != null) {
            b.menuSeek.setImageDrawable(ContextCompat.getDrawable(this, resId))
            b.menuSeek.isEnabled = enabled
        }
    }

    /**
     * Add current content (i.e. content of the currently viewed book) to the download queue
     *
     * @param quickDownload      True if the action has been triggered by a quick download
     * (which means we're not on a book gallery page but on the book list page)
     * @param isDownloadPlus     True if the action has been triggered by a "download extra pages" action
     * @param isReplaceDuplicate True if the action has been triggered by a "download and replace existing duplicate book" action
     */
    fun processDownload(
        quickDownload: Boolean,
        isDownloadPlus: Boolean,
        isReplaceDuplicate: Boolean
    ) {
        if (null == currentContent) return
        if (currentContent!!.id > 0) currentContent = dao.selectContent(currentContent!!.id)
        if (null == currentContent) return
        if (!isDownloadPlus && StatusContent.DOWNLOADED == currentContent!!.status) {
            toast(R.string.already_downloaded)
            if (!quickDownload) setActionMode(ActionMode.READ)
            return
        }
        var replacementTitle: String? = null
        if (isDownloadPlus) {
            // Copy the _current_ content's download params to the extra images
            val downloadParamsStr = currentContent!!.downloadParams
            if (downloadParamsStr != null && downloadParamsStr.length > 2) {
                for (i in extraImages) i.downloadParams = downloadParamsStr
            }

            // Determine base book : browsed downloaded book or best duplicate ?
            if (!isInLibrary(currentContent!!.status) && duplicateId > 0) {
                currentContent = dao.selectContent(duplicateId)
                if (null == currentContent) return
            }

            // Append additional pages & chapters to the base book's list of pages & chapters
            val updatedImgs: MutableList<ImageFile> = ArrayList() // Entire image set to update
            val existingImageUrls: MutableSet<String> = HashSet() // URLs of known images
            val existingChapterOrders: MutableSet<Int> = HashSet() // Positions of known chapters
            currentContent?.imageFiles?.let {
                existingImageUrls.addAll(it.map { img -> img.url })
                existingChapterOrders.addAll(
                    it.map { img ->
                        if (null == img.chapter) return@map -1
                        if (null == img.chapter!!.target) return@map -1
                        img.chapter!!.target.order
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
                currentContent!!.setImageFiles(updatedImgs)
                // Update content title if extra pages are found and title has changed
                if (StringHelper.protect(onlineContentTitle).isNotEmpty()
                    && !onlineContentTitle.equals(currentContent!!.title, ignoreCase = true)
                ) replacementTitle = onlineContentTitle
            }
            // Save additional chapters to stored book
            val additionalNonExistingChapters =
                additionalNonExistingImages.mapNotNull { img -> img.linkedChapter }
                    .filterNot { ch -> existingChapterOrders.contains(ch.order) }
            if (additionalNonExistingChapters.isNotEmpty()) {
                val updatedChapters = currentContent!!.chaptersList.toMutableList()
                updatedChapters.addAll(additionalNonExistingChapters)
                currentContent!!.setChapters(updatedChapters)
            }
            currentContent!!.status = StatusContent.SAVED
            dao.insertContent(currentContent!!)
        } // isDownloadPlus

        // Check if the tag blocker applies here
        val blockedTagsLocal = getBlockedTags(currentContent!!)
        if (blockedTagsLocal.isNotEmpty()) {
            if (Preferences.getTagBlockingBehaviour() == Constant.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE) { // Stop right here
                toast(R.string.blocked_tag, blockedTagsLocal[0])
            } else { // Insert directly as an error
                val errors: MutableList<ErrorRecord> = ArrayList()
                errors.add(
                    ErrorRecord(
                        ErrorType.BLOCKED,
                        currentContent!!.url,
                        "tags",
                        "blocked tags : " + TextUtils.join(", ", blockedTagsLocal),
                        Instant.now()
                    )
                )
                currentContent!!.setErrorLog(errors)
                currentContent!!.downloadMode = Preferences.getBrowserDlAction()
                currentContent!!.status = StatusContent.ERROR
                if (isReplaceDuplicate) currentContent!!.setContentIdToReplace(duplicateId)
                dao.insertContent(currentContent!!)
                toast(R.string.blocked_tag_queued, blockedTagsLocal[0])
                setActionMode(ActionMode.VIEW_QUEUE)
            }
            return
        }
        val replacementTitleFinal = replacementTitle
        // No reason to block or ignore -> actually add to the queue
        if (Preferences.getQueueNewDownloadPosition() == Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK && Preferences.getBrowserDlAction() == Constant.DL_ACTION_ASK) {
            show(
                this, webView, this
            ) { position1, _ ->
                show(
                    this,
                    webView, this,
                    { position2, _ ->
                        addToQueue(
                            if (0 == position1) QueuePosition.TOP else QueuePosition.BOTTOM,
                            if (0 == position2) DownloadMode.DOWNLOAD else DownloadMode.STREAM,
                            isReplaceDuplicate,
                            replacementTitleFinal
                        )
                    }, null
                )
            }
        } else if (Preferences.getQueueNewDownloadPosition() == Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            show(
                this, webView, this
            ) { position, _ ->
                addToQueue(
                    if (0 == position) QueuePosition.TOP else QueuePosition.BOTTOM,
                    Preferences.getBrowserDlAction(),
                    isReplaceDuplicate,
                    replacementTitleFinal
                )
            }
        } else if (Preferences.getBrowserDlAction() == Constant.DL_ACTION_ASK) {
            show(
                this,
                webView, this,
                { position, _ ->
                    addToQueue(
                        QueuePosition.entries.first { it.value == Preferences.getQueueNewDownloadPosition() },
                        if (0 == position) DownloadMode.DOWNLOAD else DownloadMode.STREAM,
                        isReplaceDuplicate,
                        replacementTitleFinal
                    )
                }, null
            )
        } else {
            addToQueue(
                QueuePosition.entries.first { it.value == Preferences.getQueueNewDownloadPosition() },
                Preferences.getBrowserDlAction(),
                isReplaceDuplicate,
                replacementTitleFinal
            )
        }
    }

    /**
     * Add current content to the downloads queue
     *
     * @param position           Target position in the queue (top or bottom)
     * @param downloadMode       Download mode for this content
     * @param isReplaceDuplicate True if existing duplicate book has to be replaced upon download completion
     */
    private fun addToQueue(
        position: QueuePosition,
        @DownloadMode downloadMode: Int,
        isReplaceDuplicate: Boolean,
        replacementTitle: String?
    ) {
        if (null == currentContent) return
        binding?.apply {
            val coords = Helper.getCenter(quickDlFeedback)
            if (coords != null && View.VISIBLE == quickDlFeedback.visibility) {
                Helper.setMargins(
                    animatedCheck,
                    coords.x - animatedCheck.width / 2,
                    coords.y - animatedCheck.height / 2,
                    0,
                    0
                )
            } else {
                Helper.setMargins(
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
        currentContent!!.downloadMode = downloadMode
        dao.addContentToQueue(
            currentContent!!,
            null,
            null,
            position,
            if (isReplaceDuplicate) duplicateId else -1,
            replacementTitle,
            isQueueActive(this)
        )
        if (Preferences.isQueueAutostart()) resumeQueue(this)
        setActionMode(ActionMode.VIEW_QUEUE)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            val webBFL = webView.copyBackForwardList()
            val originalUrl = StringHelper.protect(webView.originalUrl)
            var i = webBFL.currentIndex
            do {
                i--
            } while (i >= 0 && originalUrl == webBFL.getItemAtIndex(i).originalUrl)
            if (webView.canGoBackOrForward(i - webBFL.currentIndex)) {
                webView.goBackOrForward(i - webBFL.currentIndex)
            } else {
                super.onBackPressed()
            }
            return true
        }
        return false
    }

    /**
     * Display webview controls according to designated content
     *
     * @param onlineContent Currently displayed content
     * @return The status of the Content after being processed
     */
    private fun processContent(onlineContent: Content, quickDownload: Boolean): ContentStatus {
        Helper.assertNonUiThread()
        if (onlineContent.url.isEmpty()) return ContentStatus.UNDOWNLOADABLE
        if (onlineContent.status != null && onlineContent.status == StatusContent.IGNORED) return ContentStatus.UNDOWNLOADABLE
        currentContent = null
        Timber.i("Content Site, URL : %s, %s", onlineContent.site.code, onlineContent.url)
        val searchUrl =
            if (getStartSite().hasCoverBasedPageUpdates()) onlineContent.coverImageUrl else ""
        // TODO manage DB calls concurrency to avoid getting read transaction conflicts
        val contentDB =
            dao.selectContentByUrlOrCover(onlineContent.site, onlineContent.url, searchUrl)
        val isInCollection = contentDB != null && isInLibrary(contentDB.status)
        val isInQueue = contentDB != null && isInQueue(contentDB.status)
        if (!isInCollection && !isInQueue) {
            if (Preferences.isDownloadDuplicateAsk() && onlineContent.coverImageUrl.isNotEmpty()) {
                // Index the content's cover picture
                var pHash = Long.MIN_VALUE
                try {
                    val requestHeadersList: List<Pair<String, String>> = ArrayList()
                    val downloadParams =
                        parseDownloadParams(onlineContent.downloadParams).toMutableMap()
                    downloadParams[HEADER_COOKIE_KEY] =
                        getCookies(onlineContent.coverImageUrl)
                    downloadParams[HEADER_REFERER_KEY] = onlineContent.site.url
                    val onlineCover = getOnlineResourceFast(
                        fixUrl(
                            onlineContent.coverImageUrl,
                            getStartUrl()
                        ),
                        requestHeadersList,
                        getStartSite().useMobileAgent(),
                        getStartSite().useHentoidAgent(),
                        getStartSite().useWebviewAgent()
                    )
                    val coverBody = onlineCover.body
                    if (coverBody != null) {
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
                        this,
                        onlineContent,
                        Preferences.isDuplicateBrowserUseTitle(),
                        Preferences.isDuplicateBrowserUseArtist(),
                        Preferences.isDuplicateBrowserUseSameLanguage(),
                        Preferences.isDuplicateBrowserUseCover(),
                        Preferences.getDuplicateBrowserSensitivity(),
                        pHash,
                        dao
                    )
                    if (duplicateResult != null) {
                        duplicateId = duplicateResult.first.id
                        duplicateSimilarity = duplicateResult.second
                        // Content ID of the duplicate candidate of the currently viewed Content
                        val duplicateSameSite = duplicateResult.first.site == onlineContent.site
                        // Same site and similar => enable download button by default, but look for extra pics just in case
                        if (duplicateSameSite && Preferences.isDownloadPlusDuplicateTry() && !quickDownload) searchForExtraImages(
                            duplicateResult.first,
                            onlineContent
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
            if (null == contentDB) {    // The book has just been detected -> finalize before saving in DB
                onlineContent.status = StatusContent.SAVED
                addContent(this, dao, onlineContent)
            } else {
                currentContent = contentDB
            }
        } else {
            currentContent = contentDB
        }
        if (null == currentContent) currentContent = onlineContent
        if (isInCollection) {
            if (!quickDownload) searchForExtraImages(contentDB!!, onlineContent)
            return ContentStatus.IN_COLLECTION
        }
        return if (isInQueue) ContentStatus.IN_QUEUE else ContentStatus.UNKNOWN
    }

    override fun onContentReady(result: Content, quickDownload: Boolean) {
        // TODO Cancel whichever process was happening before
        if (Preferences.isBrowserMode()) return

        lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    processContent(result, quickDownload)
                }
                onContentProcessed(status, quickDownload)
            } catch (t: Throwable) {
                Timber.e(t)
                onContentProcessed(ContentStatus.UNKNOWN, false)
            }
        }
    }

    private fun onContentProcessed(status: ContentStatus, quickDownload: Boolean) {
        binding?.quickDlFeedback?.visibility = View.INVISIBLE
        if (null == currentContent) return
        when (status) {
            ContentStatus.UNDOWNLOADABLE -> onResultFailed()
            ContentStatus.UNKNOWN -> {
                if (quickDownload) {
                    if (duplicateId > -1 && Preferences.isDownloadDuplicateAsk()) DuplicateDialogFragment.invoke(
                        this,
                        duplicateId,
                        currentContent!!.qtyPages,
                        duplicateSimilarity,
                        false
                    ) else processDownload(
                        quickDownload = true,
                        isDownloadPlus = false,
                        isReplaceDuplicate = false
                    )
                } else setActionMode(ActionMode.DOWNLOAD)
            }

            ContentStatus.IN_COLLECTION -> {
                if (quickDownload) toast(R.string.already_downloaded)
                setActionMode(ActionMode.READ)
            }

            ContentStatus.IN_QUEUE -> {
                if (quickDownload) toast(R.string.already_queued)
                setActionMode(ActionMode.VIEW_QUEUE)
            }
        }
        blockedTags = getBlockedTags(currentContent!!).toMutableList()
    }

    override fun onNoResult() {
        runOnUiThread { disableActions() }
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
        val onlineImgs = parser.parseImageList(onlineContent, storedContent)
        if (onlineImgs.isEmpty()) return result
        var maxStoredImageOrder = 0
        if (storedContent.imageFiles != null) {
            val opt = storedContent.imageFiles!!
                .filter { i: ImageFile -> isInLibrary(i.status) }
                .maxOfOrNull { img -> img.order }

            if (opt != null) maxStoredImageOrder = opt
        }
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
        val storedChapters: List<Chapter>? = storedContent.chapters
        if (positionMap.isNotEmpty() && minOnlineImageOrder < maxStoredImageOrder && storedChapters.isNullOrEmpty()) {
            val storedImages = storedContent.imageList
            for (img in storedImages) {
                if (null == img.linkedChapter) {
                    val targetChapter = positionMap[img.order]
                    if (targetChapter != null) img.setChapter(targetChapter)
                }
            }
            dao.insertImageFiles(storedImages)
        }

        // Online book has more pictures than stored book -> that's what we're looking for
        return if (maxOnlineImageOrder > maxStoredImageOrder) {
            onlineImgs.filter { img -> img.order > maxStoredImageOrderFinal }.distinct()
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
                ContextCompat.getColor(
                    baseContext,
                    R.color.green
                )
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
            storedContent.imageFiles?.let {
                storedUrls.addAll(it
                    .filter { img -> isInLibrary(img.status) }
                    .map { obj: ImageFile -> obj.url }.toList()
                )
            }
            // Memorize the title of the online content (to update title of stored book later)
            onlineContentTitle = onlineContent.title

            // Display the "download more" button only if extra images URLs aren't duplicates
            val additionalNonDownloadedImages =
                additionalImages.filterNot { img -> storedUrls.contains(img.url) }
            if (additionalNonDownloadedImages.isNotEmpty()) {
                extraImages = additionalNonDownloadedImages.toMutableList()
                setActionMode(ActionMode.DOWNLOAD_PLUS)
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
            downloadedBooksUrls.addAll(
                dao.selectAllSourceUrls(getStartSite())
                    .map { url -> simplifyUrl(url) }
                    .filterNot { obj: String -> obj.isEmpty() }
            )
        }
    }

    private fun updateMergedBooksUrls() {
        synchronized(mergedBooksUrls) {
            mergedBooksUrls.clear()
            mergedBooksUrls.addAll(
                dao.selectAllMergedUrls(getStartSite())
                    .asSequence()
                    .map { s -> s.replace(getStartSite().url, "") }
                    .map { s ->
                        s.replace(
                            "\\b|/galleries|/gallery|/g|/entry\\b".toRegex(),
                            ""
                        )
                    } //each sites "gallery" path
                    .map { url -> simplifyUrl(url) }
                    .filterNot { obj: String -> obj.isEmpty() }
            )
        }
    }

    private fun updatePrefBlockedTags() {
        m_prefBlockedTags = Preferences.getBlockedTags()
    }

    private fun clearPrefBlockedTags() {
        m_prefBlockedTags.clear()
    }

    /**
     * Listener for the events of the download engine
     * Used to switch the action button to Read when the download of the currently viewed is completed
     *
     * @param event Event fired by the download engine
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        if (event.eventType === DownloadEvent.Type.EV_COMPLETE) {
            if (webClient.isMarkDownloaded()) updateDownloadedBooksUrls()
            if (event.content != null && event.content == currentContent && event.content!!.status == StatusContent.DOWNLOADED) {
                setActionMode(ActionMode.READ)
            }
        }
    }

    fun tooltip(@StringRes resource: Int, always: Boolean) {
        binding?.apply {
            this@BaseWebActivity.showTooltip(
                resource,
                ArrowOrientation.BOTTOM,
                bottomNavigation,
                this@BaseWebActivity,
                always
            )
        }
    }

    override fun onDownloadDuplicate(actionMode: DuplicateDialogFragment.ActionMode) {
        processDownload(
            false,
            actionMode == DuplicateDialogFragment.ActionMode.DOWNLOAD_PLUS,
            actionMode == DuplicateDialogFragment.ActionMode.REPLACE
        )
    }


    /**
     * Indicate if the browser's back list contains a book gallery
     * Used to determine the display of the "back to latest gallery" button
     *
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
        val intent = Intent(this, PrefsActivity::class.java)
        val prefsBundle = PrefsBundle()
        prefsBundle.isBrowserPrefs = true
        intent.putExtras(prefsBundle.bundle)
        startActivity(intent)
    }

    /**
     * Show the About page
     */
    private fun onAboutClick() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    override val allSiteUrls: List<String>
        get() = downloadedBooksUrls.toMutableList() // Work on a copy to avoid any thread-synch issue
    override val allMergedBooksUrls: List<String>
        get() = mergedBooksUrls.toMutableList()
    override val prefBlockedTags: List<String>
        get() = m_prefBlockedTags.toMutableList()
    override val customCss: String
        get() = computeCustomCss()
    override val alertStatus: AlertStatus
        get() = alert?.getStatus() ?: AlertStatus.NONE
    override val scope: LifecycleCoroutineScope
        get() = lifecycleScope

    private fun computeCustomCss(): String {
        if (null == m_customCss) {
            val sb = StringBuilder()
            if (Preferences.isBrowserMarkDownloaded() || Preferences.isBrowserMarkMerged() || Preferences.isBrowserMarkBlockedTags()) getAssetAsString(
                assets, "downloaded.css", sb
            )
            if (getStartSite() == Site.NHENTAI && Preferences.isBrowserNhentaiInvisibleBlacklist()) getAssetAsString(
                assets, "nhentai_invisible_blacklist.css", sb
            )
            if (getStartSite() == Site.IMHENTAI) getAssetAsString(
                assets, "imhentai.css", sb
            )
            if (getStartSite() == Site.KSK) getAssetAsString(
                assets, "ksk.css", sb
            )
            if (getStartSite() == Site.PIXIV && Settings.isBrowserAugmented) getAssetAsString(
                assets, "pixiv.css", sb
            )
            m_customCss = sb.toString()
        }
        return m_customCss!!
    }


    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param key   Key that has been changed
     */
    private fun onSharedPreferenceChanged(key: String?) {
        var reload = false
        if (Preferences.Key.BROWSER_DL_ACTION == key) {
            downloadIcon =
                if (Preferences.getBrowserDlAction() == DownloadMode.STREAM) R.drawable.selector_download_stream_action else R.drawable.selector_download_action
            setActionMode(actionButtonMode)
        } else if (Preferences.Key.BROWSER_MARK_DOWNLOADED == key) {
            m_customCss = null
            webClient.setMarkDownloaded(Preferences.isBrowserMarkDownloaded())
            if (webClient.isMarkDownloaded()) updateDownloadedBooksUrls()
            reload = true
        } else if (Preferences.Key.BROWSER_MARK_MERGED == key) {
            m_customCss = null
            webClient.setMarkMerged(Preferences.isBrowserMarkMerged())
            if (webClient.isMarkMerged()) updateMergedBooksUrls()
            reload = true
        } else if (Preferences.Key.BROWSER_MARK_BLOCKED == key) {
            m_customCss = null
            webClient.setMarkBlockedTags(Preferences.isBrowserMarkBlockedTags())
            if (webClient.isMarkBlockedTags()) updatePrefBlockedTags() else clearPrefBlockedTags()
            reload = true
        } else if (Preferences.Key.DL_BLOCKED_TAGS == key) {
            updatePrefBlockedTags()
            reload = true
        } else if (Preferences.Key.BROWSER_NHENTAI_INVISIBLE_BLACKLIST == key) {
            m_customCss = null
            reload = true
        } else if (Preferences.Key.BROWSER_DNS_OVER_HTTPS == key) {
            webClient.setDnsOverHttpsEnabled(Preferences.getDnsOverHttps() > -1)
            reload = true
        } else if (Preferences.Key.BROWSER_QUICK_DL == key) {
            if (Preferences.isBrowserQuickDl()) webView.setOnLongTapListener { x: Int, y: Int ->
                onLongTap(x, y)
            } else webView.setOnLongTapListener(null)
        } else if (Preferences.Key.BROWSER_QUICK_DL_THRESHOLD == key) {
            webView.setLongClickThreshold(Preferences.getBrowserQuickDlThreshold())
        } else if (Settings.Key.WEB_ADBLOCKER == key) {
            if (Settings.isAdBlockerOn && !Settings.isBrowserAugmented)
                Settings.isBrowserAugmented = true
            updateAdblockButton(Settings.isAdBlockerOn)
            webClient.adBlocker.setActive(Settings.isAdBlockerOn)
            webView.reload()
        }
        if (reload && !webClient.isLoading()) webView.reload()
    }

    // References :
    // https://stackoverflow.com/a/64961272/8374722
    // https://stackoverflow.com/questions/3941969/android-intercept-ajax-call-from-webview/5742194
    class FetchHandler(private val handler: BiConsumer<String, String>) {
        @JavascriptInterface
        @Suppress("unused")
        fun onFetchCall(url: String, body: String) {
            Timber.d("fetch Begin %s : %s", url, body)
            handler.invoke(url, body)
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