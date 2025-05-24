package me.devsaki.hentoid.activities.sources

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AlertStatus
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.selectX
import me.devsaki.hentoid.util.AdBlocker
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.duplicateInputStream
import me.devsaki.hentoid.util.file.getAssetAsString
import me.devsaki.hentoid.util.file.openFile
import me.devsaki.hentoid.util.file.saveBinary
import me.devsaki.hentoid.util.getRandomInt
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP
import me.devsaki.hentoid.util.image.bitmapToWebp
import me.devsaki.hentoid.util.image.getBitmapFromVectorDrawable
import me.devsaki.hentoid.util.image.tintBitmap
import me.devsaki.hentoid.util.isPresentAsWord
import me.devsaki.hentoid.util.network.HEADER_CONTENT_TYPE
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.cleanContentType
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getChromeVersion
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getExtensionFromUri
import me.devsaki.hentoid.util.network.getOnlineResource
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import me.devsaki.hentoid.util.network.okHttpResponseToWebkitResponse
import me.devsaki.hentoid.util.network.setCookies
import me.devsaki.hentoid.util.network.simplifyUrl
import me.devsaki.hentoid.util.network.webkitRequestHeadersToOkHttpHeaders
import me.devsaki.hentoid.util.parseDownloadParams
import me.devsaki.hentoid.util.serializeToJson
import me.devsaki.hentoid.util.toast
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.HtmlAdapter
import pl.droidsonroids.jspoon.Jspoon
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Analyze loaded HTML to display download button
 * Override blocked content with empty content
 */
open class CustomWebViewClient : WebViewClient {

    // Site for the session
    val site: Site

    // Listener to the results of the page parser
    protected val activity: CustomWebActivity?

    private val scope: CoroutineScope

    // Listener to the results of the page parser
    protected val resConsumer: WebResultConsumer? // TODO remove if unused in v1.21.x

    // List of the URL patterns identifying a parsable book gallery page
    // TODO differentiate API call URLs and HTML gallery URLs
    private val galleryUrlPattern: MutableList<Pattern> = ArrayList()

    // List of the URL patterns identifying a parsable book gallery page
    private val resultsUrlPattern: MutableList<Pattern> = ArrayList()

    // Results URL rewriter to insert page to seek to
    private var resultsUrlRewriter: ((Uri, Int) -> String)? = null

    // Adapter used to parse the HTML code of book gallery pages
    private val htmlAdapter: HtmlAdapter<out ContentParser>

    // Domain name for which link navigation is restricted
    private val restrictedDomainNames: MutableList<String> = ArrayList()

    // Loading state of the current webpage (used for the refresh/stop feature)
    private val isPageLoading = AtomicBoolean(false)

    // Loading state of the HTML code of the current webpage (used to trigger the action button)
    private val isHtmlLoaded = AtomicBoolean(false)

    // URL string of the main page (used for custom CSS loading)
    private var mainPageUrl: String? = null

    val adBlocker: AdBlocker

    // Faster access to Preferences settings
    private val markDownloaded = AtomicBoolean(Settings.isBrowserMarkDownloaded)
    private val markMerged = AtomicBoolean(Settings.isBrowserMarkMerged)
    private val markQueued = AtomicBoolean(Settings.isBrowserMarkQueued)
    private val markBlockedTags = AtomicBoolean(Settings.isBrowserMarkBlockedTags)
    private val dnsOverHttpsEnabled = AtomicBoolean(Settings.dnsOverHttps > -1)


    // List of elements (CSS selector) to be removed before displaying the page
    private val removableElements: MutableList<String> by lazy { ArrayList() }

    // List of blacklisted Javascript strings : if any of these is found inside
    // an inline script tag, the entire tag is removed from the HTML
    private val jsContentBlacklist: MutableList<String> by lazy { ArrayList() }

    // Custom method to use while pre-processing HTML
    private var customHtmlRewriter: Consumer<Document>? = null

    // List of JS scripts to load from app resources every time a webpage is started
    private val jsStartupScripts: MutableList<String> by lazy { ArrayList() }
    private val jsReplacements: MutableMap<String, String> by lazy { HashMap() }


    companion object {
        // Represent WEBP binary data for the checkmark icon used to mark downloaded books
        // (will be fed directly to the browser when the resourcei is requested)
        val CHECKMARK = bitmapToWebp(
            tintBitmap(
                getBitmapFromVectorDrawable(
                    HentoidApp.getInstance(), R.drawable.ic_checked
                ), ContextCompat.getColor(HentoidApp.getInstance(), R.color.secondary_light)
            )
        )

        // this is for merged books
        val MERGED_MARK = bitmapToWebp(
            tintBitmap(
                getBitmapFromVectorDrawable(
                    HentoidApp.getInstance(), R.drawable.ic_action_merge
                ), ContextCompat.getColor(HentoidApp.getInstance(), R.color.secondary_light)
            )
        )

        // this is for queued books
        val QUEUED_MARK = bitmapToWebp(
            tintBitmap(
                getBitmapFromVectorDrawable(
                    HentoidApp.getInstance(), R.drawable.ic_action_queue
                ), ContextCompat.getColor(HentoidApp.getInstance(), R.color.secondary_light)
            )
        )

        // this is for books with blocked tags;
        val BLOCKED_MARK = bitmapToWebp(
            tintBitmap(
                getBitmapFromVectorDrawable(
                    HentoidApp.getInstance(), R.drawable.ic_forbidden
                ), ContextCompat.getColor(HentoidApp.getInstance(), R.color.secondary_light)
            )
        )

        // Pre-built object to represent an empty input stream
        // (will be used instead of the actual stream when the requested resource is blocked)
        val NOTHING = ByteArray(0)
    }

    @OptIn(DelicateCoroutinesApi::class)
    constructor(
        site: Site,
        galleryUrl: Array<String>
    ) {
        this.site = site
        activity = null
        scope = GlobalScope
        this.resConsumer = null
        for (s in galleryUrl) galleryUrlPattern.add(Pattern.compile(s))
        htmlAdapter = initJspoon(site)
        adBlocker = AdBlocker(site)
    }

    constructor(
        site: Site,
        galleryUrl: Array<String>,
        activity: CustomWebActivity
    ) {
        this.site = site
        this.activity = activity
        scope = activity.scope
        resConsumer = activity
        for (s in galleryUrl) galleryUrlPattern.add(Pattern.compile(s))
        htmlAdapter = initJspoon(site)
        adBlocker = AdBlocker(site)
    }

    private fun initJspoon(site: Site): HtmlAdapter<out ContentParser> {
        val c = ContentParserFactory.getContentParserClass(site)
        val jspoon = Jspoon.create()
        return jspoon.adapter(c) // Unchecked but alright
    }

    open fun destroy() {
        Timber.d("WebClient destroyed")
    }

    /**
     * Add an element filter to current site
     *
     * @param elements Elements (CSS selector) to addAll to page cleaner
     */
    fun addRemovableElements(vararg elements: String) {
        removableElements.addAll(elements)
    }

    /**
     * Add a Javascript blacklisted element filter to current site
     *
     * @param elements Elements (string) to addAll to page cleaner
     */
    fun addJavascriptBlacklist(vararg elements: String) {
        jsContentBlacklist.addAll(elements)
    }

    /**
     * Set the list of patterns to detect URLs where result paging can be applied
     *
     * @param patterns Patterns to detect URLs where result paging can be applied
     */
    fun setResultsUrlPatterns(vararg patterns: String) {
        for (s in patterns) resultsUrlPattern.add(Pattern.compile(s))
    }

    /**
     * Set the rewriter to use when paging results from the app :
     * - 1st argument : Search results page URL, as an Uri
     * - 2nd argument : Search results page number to reach
     * - Result : Modified Uri, as a string
     *
     * @param rewriter Rewriter to use when paging results from the app
     */
    fun setResultUrlRewriter(rewriter: (Uri, Int) -> String) {
        resultsUrlRewriter = rewriter
    }

    fun setCustomHtmlRewriter(rewriter: Consumer<Document>) {
        customHtmlRewriter = rewriter
    }

    /**
     * Set the list of JS scripts (app assets) to load at each new page start
     *
     * @param assetNames Name of assets to load
     */
    fun setJsStartupScripts(vararg assetNames: String) {
        jsStartupScripts.addAll(assetNames)
    }

    fun addJsReplacement(source: String, target: String) {
        jsReplacements[source] = target
    }

    /**
     * Restrict link navigation to a given domain name
     *
     * @param s Domain name to restrict link navigation to
     */
    protected fun restrictTo(s: String) {
        restrictedDomainNames.add(s)
    }

    fun restrictTo(vararg s: String) {
        restrictedDomainNames.addAll(s)
    }

    private fun isHostNotInRestrictedDomains(host: String): Boolean {
        if (restrictedDomainNames.isEmpty()) return false
        for (s in restrictedDomainNames) {
            if (host.contains(s)) return false
        }
        Timber.i("Unrestricted host detected : %s", host)
        return true
    }

    /**
     * Indicates if the given URL is a book gallery page
     *
     * @param url URL to test
     * @return True if the given URL represents a book gallery page
     */
    open fun isGalleryPage(url: String): Boolean {
        if (galleryUrlPattern.isEmpty()) return false
        for (p in galleryUrlPattern) {
            val matcher = p.matcher(url)
            if (matcher.find()) return true
        }
        return false
    }

    /**
     * Indicates if the given URL is a results page
     *
     * @param url URL to test
     * @return True if the given URL represents a results page
     */
    private fun isResultsPage(url: String): Boolean {
        if (resultsUrlPattern.isEmpty()) return false
        for (p in resultsUrlPattern) {
            val matcher = p.matcher(url)
            if (matcher.find()) return true
        }
        return false
    }

    /**
     * Rewrite the given URL to seek the given page number
     *
     * @param url     URL to be rewritten
     * @param pageNum page number to seek
     * @return Given URL to be rewritten
     */
    fun seekResultsUrl(url: String, pageNum: Int): String {
        return if (null == resultsUrlRewriter || !isResultsPage(url) || isGalleryPage(url)) url
        else resultsUrlRewriter!!.invoke(url.toUri(), pageNum)
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
    private fun canUseSingleOkHttpRequest(): Boolean {
        return (Settings.isBrowserAugmented(site) && (getChromeVersion() < 45 || getChromeVersion() > 71))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return shouldOverrideUrlLoadingInternal(
            view, request.url.toString(), request.requestHeaders, request.isForMainFrame
        )
    }

    private fun shouldOverrideUrlLoadingInternal(
        view: WebView, url: String, headers: Map<String, String>?, isMainPage: Boolean
    ): Boolean {
        if (Settings.isBrowserAugmented(site)
            && (!isMainPage && adBlocker.isBlocked(url, headers)) // Don't block the main page
            || !url.startsWith("http")
        ) return true

        // Download and open the torrent file
        // NB : Opening the URL itself won't work when the tracker is private
        // as the 3rd party torrent app doesn't have access to it
        if (getExtensionFromUri(url) == "torrent") {
            view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                try {
                    val uri = withContext(Dispatchers.IO) {
                        downloadFile(view.context, url, headers)
                    }
                    openFile(view.context, uri)
                } catch (t: Throwable) {
                    view.context.toast(R.string.torrent_dl_fail, t.message ?: "")
                    Timber.w(t)
                }
            }
        }
        val host = url.toUri().host
        return host != null && isHostNotInRestrictedDomains(host)
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
    @Throws(IOException::class)
    private fun downloadFile(
        context: Context, url: String, requestHeaders: Map<String, String>?
    ): File {
        val requestHeadersList = webkitRequestHeadersToOkHttpHeaders(requestHeaders, url)
        getOnlineResource(
            url,
            requestHeadersList,
            site.useMobileAgent,
            site.useHentoidAgent,
            site.useWebviewAgent
        ).use { onlineFileResponse ->
            val body = onlineFileResponse.body ?: throw IOException("Empty response from server")
            val cacheDir = context.cacheDir
            // Using a random file name rather than the original name to avoid errors caused by path length
            val file = File(
                cacheDir.absolutePath + File.separator + getRandomInt(
                    10000
                ) + "." + getExtensionFromUri(url)
            )
            if (!file.createNewFile()) throw IOException("Couldn't create file")
            val torrentFileUri = Uri.fromFile(file)
            saveBinary(context, torrentFileUri, body.bytes())
            return file
        }
    }

    /**
     * Important note
     *
     * Based on observation, for a given URL, onPageStarted seems to be called
     * - Before [shouldInterceptRequest] when the page is not cached (1st call)
     * - After [shouldInterceptRequest] when the page is cached (Nth call; N>1)
     */
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (BuildConfig.DEBUG) Timber.v("WebView : page started %s", url)
        isPageLoading.set(true)

        // Activate startup JS
        for (s in jsStartupScripts) view.loadUrl(getJsScript(view.context, s, jsReplacements))
        activity?.onPageStarted(url, isGalleryPage(url), isHtmlLoaded.get(), true)
    }

    override fun onPageFinished(view: WebView?, url: String) {
        if (BuildConfig.DEBUG) Timber.v("WebView : page finished %s", url)
        isPageLoading.set(false)
        isHtmlLoaded.set(false) // Reset for the next page
        activity?.onPageFinished(url, isResultsPage(url), isGalleryPage(url))
    }

    /**
     * Note : this method is called by a non-UI thread
     */
    override fun shouldInterceptRequest(
        view: WebView, request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        // Data fetched with POST is out of scope of analysis and adblock
        if (!request.method.equals("get", ignoreCase = true)) {
            Timber.v("[%s] ignored by interceptor; method = %s", url, request.method)
            return sendRequest(request)
        }
        if (request.isForMainFrame) mainPageUrl = url
        val result =
            shouldInterceptRequestInternal(url, request.requestHeaders, request.isForMainFrame)
        return result ?: sendRequest(request)
    }

    /**
     * Determines if the page at the given URL is to be processed
     *
     * @param url     Called URL
     * @param headers Request headers
     * @return Processed response if the page has been processed;
     * null if vanilla processing should happen instead
     */
    private fun shouldInterceptRequestInternal(
        url: String, headers: Map<String, String>?, isMainPage: Boolean
    ): WebResourceResponse? {
        return if (Settings.isBrowserAugmented(site)
            && (!isMainPage && adBlocker.isBlocked(url, headers)) // Don't block the main page
            || !url.startsWith("http")
        ) {
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(NOTHING))
        } else if (isMarkDownloaded() && url.contains("hentoid-checkmark")) {
            WebResourceResponse(
                MIME_IMAGE_WEBP, "utf-8", ByteArrayInputStream(CHECKMARK)
            )
        } else if (isMarkMerged() && url.contains("hentoid-mergedmark")) {
            WebResourceResponse(
                MIME_IMAGE_WEBP, "utf-8", ByteArrayInputStream(MERGED_MARK)
            )
        } else if (isMarkQueued() && url.contains("hentoid-queuedmark")) {
            WebResourceResponse(
                MIME_IMAGE_WEBP, "utf-8", ByteArrayInputStream(QUEUED_MARK)
            )
        } else if (url.contains("hentoid-blockedmark")) {
            WebResourceResponse(
                MIME_IMAGE_WEBP, "utf-8", ByteArrayInputStream(BLOCKED_MARK)
            )
        } else {
            if (isGalleryPage(url)) return parseResponse(
                url,
                headers,
                analyzeForDownload = true,
                quickDownload = false
            )
            else if (BuildConfig.DEBUG) Timber.v("WebView : not gallery %s", url)

            // If we're here to remove removable elements or mark downloaded books, we only do it
            // on HTML resources (URLs without extension) from the source's main domain
            if ((removableElements.isNotEmpty() || jsContentBlacklist.isNotEmpty()
                        || isMarkDownloaded() || isMarkMerged() || isMarkQueued() || isMarkBlockedTags()
                        || activity != null && activity.customCss.isNotEmpty())
                && (getExtensionFromUri(url).isEmpty()
                        || getExtensionFromUri(url).equals("html", ignoreCase = true))
            ) {
                val host = url.toUri().host
                if (host != null && !isHostNotInRestrictedDomains(host))
                    return parseResponse(
                        url,
                        headers,
                        analyzeForDownload = false,
                        quickDownload = false
                    )
            }
            null
        }
    }

    fun sendRequest(request: WebResourceRequest): WebResourceResponse? {
        if (dnsOverHttpsEnabled.get()) {
            // Query resource using OkHttp
            val urlStr = request.url.toString()
            val requestHeadersList =
                webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, urlStr)
            try {
                getOnlineResource(
                    urlStr,
                    requestHeadersList,
                    site.useMobileAgent,
                    site.useHentoidAgent,
                    site.useWebviewAgent
                ).use { response ->
                    // Scram if the response is a redirection or an error
                    if (response.code >= 300) return null
                    val body = response.body ?: throw IOException("Empty body")
                    return okHttpResponseToWebkitResponse(response, body.byteStream())
                }
            } catch (e: IOException) {
                Timber.i(e)
            } catch (e: IllegalStateException) {
                Timber.i(e)
            }
        }
        return null
    }

    /**
     * Load the given URL using a separate thread
     *
     * @param url URL to load
     */
    private fun browserLoadAsync(url: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            activity?.loadUrl(url)
        }
    }

    /**
     * Process the webpage at the given URL
     *
     * @param url             URL of the page to process
     * @param requestHeaders     Request headers to use
     * @param analyzeForDownload True if the page has to be analyzed for potential downloads;
     * false if only ad removal should happen
     * @param quickDownload      True if the present call has been triggered by a quick download action
     * @return Processed response if the page has been actually processed;
     * null if vanilla processing should happen instead
     */
    @SuppressLint("NewApi")
    open fun parseResponse(
        url: String,
        requestHeaders: Map<String, String>?,
        analyzeForDownload: Boolean,
        quickDownload: Boolean
    ): WebResourceResponse? {
        assertNonUiThread()
        if (BuildConfig.DEBUG) Timber.v(
            "WebView : parseResponse %s %s", if (analyzeForDownload) "DL" else "", url
        )

        // If we're here for remove elements only, and can't use the OKHTTP request, it's no use going further
        if (!analyzeForDownload && !canUseSingleOkHttpRequest()) return null
        if (analyzeForDownload) activity?.onGalleryPageStarted(url)
        val requestHeadersList = webkitRequestHeadersToOkHttpHeaders(requestHeaders, url)
        var response: Response? = null
        try {
            // Query resource here, using OkHttp
            response = getOnlineResourceFast(
                url,
                requestHeadersList,
                site.useMobileAgent,
                site.useHentoidAgent,
                site.useWebviewAgent,
                false
            )
        } catch (e: MalformedURLException) {
            Timber.e(e, "Malformed URL : %s", url)
        } catch (_: SocketTimeoutException) {
            // If fast method occurred timeout, reconnect with non-fast method
            Timber.d("Timeout; Reconnect with non-fast method : %s", url)
            try {
                response = getOnlineResource(
                    url,
                    requestHeadersList,
                    site.useMobileAgent,
                    site.useHentoidAgent,
                    site.useWebviewAgent
                )
            } catch (ex: IOException) {
                Timber.e(ex)
            } catch (ex: IllegalStateException) {
                Timber.e(ex)
            }
        } catch (e: IOException) {
            Timber.e(e)
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
        if (response != null) {
            try {
                // Scram if the response is an error
                if (response.code >= 400) return null

                // Handle redirection and force the browser to reload to be able to process the page
                // NB1 : shouldInterceptRequest doesn't trigger on redirects
                // NB2 : parsing alone won't cut it because the adblocker needs the new content on the new URL
                if (response.code >= 300) {
                    var targetUrl = response.header("location") ?: ""
                    if (targetUrl.isEmpty())
                        targetUrl = response.header("Location") ?: ""
                    if (BuildConfig.DEBUG)
                        Timber.v("WebView : redirection from %s to %s", url, targetUrl)
                    if (targetUrl.isNotEmpty())
                        browserLoadAsync(fixUrl(targetUrl, site.url))
                    return null
                }

                // Scram if the response is something else than html
                val rawContentType =
                    response.header(HEADER_CONTENT_TYPE, "") ?: return null
                val contentType = cleanContentType(rawContentType)
                if (contentType.first.isNotEmpty() && contentType.first != "text/html") return null

                // Scram if the response is empty
                val body = response.body ?: throw IOException("Empty body")
                val parserStream: InputStream?
                val result: WebResourceResponse?
                if (canUseSingleOkHttpRequest()) {
                    var browserStream: InputStream
                    if (analyzeForDownload) {
                        // Response body bytestream needs to be duplicated
                        // because Jsoup closes it, which makes it unavailable for the WebView to use
                        val `is` = duplicateInputStream(body.byteStream(), 2)
                        parserStream = `is`[0]
                        browserStream = `is`[1]
                    } else {
                        parserStream = null
                        browserStream = body.byteStream()
                    }

                    // Remove removable elements from HTML resources
                    activity?.let {
                        val customCss = it.customCss
                        if (removableElements.isNotEmpty() || jsContentBlacklist.isNotEmpty() || isMarkDownloaded() || isMarkMerged() || isMarkQueued() || isMarkBlockedTags() || customCss.isNotEmpty()) {
                            browserStream = processHtml(
                                browserStream,
                                url,
                                customCss,
                                removableElements,
                                jsContentBlacklist,
                                it.allSiteUrls,
                                it.allMergedBooksUrls,
                                it.allQueuedBooksUrls,
                                it.prefBlockedTags
                            )
                        }
                    }

                    // Convert OkHttp response to the expected format
                    result = okHttpResponseToWebkitResponse(response, browserStream)

                    // Manually set cookie if present in response header (has to be set manually because we're using OkHttp right now, not the webview)
                    if (result.responseHeaders.containsKey("set-cookie")
                        || result.responseHeaders.containsKey("Set-Cookie")
                    ) {
                        var cookiesStr = result.responseHeaders["set-cookie"]
                        if (null == cookiesStr) cookiesStr = result.responseHeaders["Set-Cookie"]
                        if (cookiesStr != null) {
                            // Set-cookie might contain multiple cookies to set separated by a line feed (see HttpHelper.getValuesSeparatorFromHttpHeader)
                            val cookieParts = cookiesStr.split("\n")
                            for (cookie in cookieParts)
                                if (cookie.isNotEmpty()) setCookies(url, cookie)
                        }
                    }
                } else {
                    parserStream = body.byteStream()
                    result = null // Default webview behaviour
                }
                // If there's a red alert ongoing, don't try parsing the page
                val alert = activity?.alertStatus ?: AlertStatus.NONE
                if (analyzeForDownload && alert != AlertStatus.RED) {
                    try {
                        var content = htmlAdapter.fromInputStream(parserStream!!, URL(url))
                            .toContent(url)
                        // ProcessContent needs to be called on a new thread as it may wait for browser loading to complete
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                content = processContent(content, url, quickDownload)
                            }
                            resConsumer?.onContentReady(content, quickDownload)
                        }
                    } catch (t: Throwable) {
                        Timber.e(t, "Error parsing content.")
                        parserStream?.close()
                        isHtmlLoaded.set(true)
                        resConsumer?.onResultFailed()
                    }
                } else {
                    isHtmlLoaded.set(true)
                    resConsumer?.onNoResult()
                }
                return result
            } catch (e: IOException) {
                Timber.e(e)
            } catch (e: IllegalStateException) {
                Timber.e(e)
            }
        }
        return null
    }

    /**
     * Process Content parsed from a webpage
     *
     * @param content       Content to be processed
     */
    protected open fun processContent(
        content: Content,
        url: String,
        quickDownload: Boolean // Useless here; useful in some overrides
    ): Content {
        if (content.status == StatusContent.IGNORED) return content

        // Save useful download params for future use during download
        val params = if (content.downloadParams.length > 2) // Params already contain values
            parseDownloadParams(content.downloadParams).toMutableMap() else java.util.HashMap()
        params[HEADER_COOKIE_KEY] = getCookies(url)
        params[HEADER_REFERER_KEY] = content.site.url
        content.downloadParams = serializeToJson<Map<String, String>>(params, MAP_STRINGS)
        isHtmlLoaded.set(true)
        return content
    }

    /**
     * Indicate whether the current webpage is still loading or not
     *
     * @return True if current webpage is being loaded; false if not
     */
    fun isLoading(): Boolean {
        return isPageLoading.get()
    }

    fun isMarkDownloaded(): Boolean {
        return markDownloaded.get()
    }

    fun setMarkDownloaded(value: Boolean) {
        markDownloaded.set(value)
    }

    fun isMarkMerged(): Boolean {
        return markMerged.get()
    }

    fun setMarkMerged(value: Boolean) {
        markMerged.set(value)
    }

    fun isMarkQueued(): Boolean {
        return markQueued.get()
    }

    fun setMarkQueued(value: Boolean) {
        markQueued.set(value)
    }

    fun isMarkBlockedTags(): Boolean {
        return markBlockedTags.get()
    }

    fun setMarkBlockedTags(value: Boolean) {
        markBlockedTags.set(value)
    }

    fun setDnsOverHttpsEnabled(value: Boolean) {
        dnsOverHttpsEnabled.set(value)
    }

    /**
     * Process the given HTML document contained in the given stream :
     * - If set, remove nodes using the given list of CSS selectors to identify them
     * - If set, mark book covers or links matching the given list of Urls
     *
     * @param stream             Stream containing the HTML document to process; will be closed during the process
     * @param baseUri            Base URI of the document
     * @param removableElements  CSS selectors of the nodes to remove
     * @param jsContentBlacklist Blacklisted elements to detect script tags to remove
     * @param siteUrls           Urls of the covers or links to visually mark as downloaded
     * @param mergedSiteUrls     Urls of the covers or links to visually mark as merged
     * @param queuedSiteUrls     Urls of the covers or links to visually mark as queued
     * @param blockedTags        Tags of the preference-browser-blocked tag option to visually mark as blocked
     * @return Stream containing the HTML document stripped from the elements to remove
     */
    @Throws(IOException::class)
    private fun processHtml(
        stream: InputStream,
        baseUri: String,
        customCss: String?,
        removableElements: List<String>?,
        jsContentBlacklist: List<String>?,
        siteUrls: List<String>?,
        mergedSiteUrls: List<String>?,
        queuedSiteUrls: List<String>?,
        blockedTags: List<String>?
    ): InputStream {
        val doc = Jsoup.parse(stream, null, baseUri)
        // https://github.com/jhy/jsoup/issues/502#issuecomment-953514521
        if (1 == site.jsoupOutputSyntax) {
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
        }

        // Add custom inline CSS to the main page only
        if (customCss != null && baseUri == mainPageUrl)
            doc.head().appendElement("style").attr("type", "text/css").appendText(customCss)

        val isAdBlock = Settings.isAdBlockerOn(site) && Settings.isBrowserAugmented(site)

        // Remove ad spaces
        if (isAdBlock && removableElements != null)
            for (s in removableElements)
                for (e in doc.selectX(s)) {
                    Timber.d("[%s] Removing node %s", baseUri, e.toString())
                    e.remove()
                }

        // Remove scripts
        if (isAdBlock && jsContentBlacklist != null) {
            for (e in doc.select("script")) {
                val scriptContent = e.toString().lowercase(Locale.getDefault())
                for (s in jsContentBlacklist) {
                    if (scriptContent.contains(s.lowercase(Locale.getDefault()))) {
                        Timber.d("[%s] Removing script %s", baseUri, e.toString())
                        e.remove()
                        break
                    }
                }
            }
        }

        // Mark downloaded books, merged books and queued books
        if (siteUrls != null && mergedSiteUrls != null && queuedSiteUrls != null && (siteUrls.isNotEmpty() || mergedSiteUrls.isNotEmpty() || queuedSiteUrls.isNotEmpty())) {
            // Format elements
            val plainLinks = doc.select("a")
            val linkedImages = doc.select("a img")

            // Key = simplified HREF
            // Value.left = plain link ("a")
            // Value.right = corresponding linked images ("a img"), if any
            val elements: MutableMap<String, Pair<Element, Element?>> = HashMap()
            for (link in plainLinks) {
                if (site.bookCardExcludedParentClasses.isNotEmpty()) {
                    val isForbidden = link.parents().any { e: Element ->
                        containsForbiddenClass(site, e.classNames())
                    }
                    if (isForbidden) continue
                }
                val aHref = simplifyUrl(link.attr("href"))
                if (aHref.isNotEmpty() && !elements.containsKey(aHref)) // We only process the first match - usually the cover
                    elements[aHref] = Pair(link, null)
            }
            for (linkedImage in linkedImages) {
                var parent = linkedImage.parent()
                while (parent != null && !parent.`is`("a")) parent = parent.parent()
                if (null == parent) break
                if (site.bookCardExcludedParentClasses.isNotEmpty()) {
                    val isForbidden = parent.parents().any { e: Element ->
                        containsForbiddenClass(site, e.classNames())
                    }
                    if (isForbidden) continue
                }
                val aHref = simplifyUrl(parent.attr("href"))
                val elt = elements[aHref]
                if (elt != null && null == elt.second) // We only process the first match - usually the cover
                    elements[aHref] = Pair(elt.first, linkedImage)
            }
            elements.forEach { (key, value) ->
                for (url in siteUrls) {
                    if (key.endsWith(url)) {
                        // Linked images have priority over plain links
                        var markedElement = value.second
                        if (markedElement != null) { // Mark <site.bookCardDepth> levels above the image
                            var imgParent = markedElement.parent()
                            for (i in 0 until site.bookCardDepth - 1)
                                if (imgParent != null) imgParent = imgParent.parent()
                            if (imgParent != null) markedElement = imgParent
                        } else { // Mark plain link
                            markedElement = value.first
                        }
                        markedElement.addClass("watermarked")
                        break
                    }
                }
                for (url in mergedSiteUrls) {
                    if (key.endsWith(url)) {
                        // Linked images have priority over plain links
                        var markedElement = value.second
                        if (markedElement != null) { // Mark <site.bookCardDepth> levels above the image
                            var imgParent = markedElement.parent()
                            for (i in 0 until site.bookCardDepth - 1) if (imgParent != null) imgParent =
                                imgParent.parent()
                            if (imgParent != null) markedElement = imgParent
                        } else { // Mark plain link
                            markedElement = value.first
                        }
                        markedElement.addClass("watermarked-merged")
                        break
                    }
                }
                for (url in queuedSiteUrls) {
                    if (key.endsWith(url)) {
                        // Linked images have priority over plain links
                        var markedElement = value.second
                        if (markedElement != null) { // Mark <site.bookCardDepth> levels above the image
                            var imgParent = markedElement.parent()
                            for (i in 0 until site.bookCardDepth - 1) if (imgParent != null) imgParent =
                                imgParent.parent()
                            if (imgParent != null) markedElement = imgParent
                        } else { // Mark plain link
                            markedElement = value.first
                        }
                        markedElement.addClass("watermarked-queued")
                        break
                    }
                }
            }
        }

        // Mark books with blocked tags
        if (!blockedTags.isNullOrEmpty()) {
            val plainLinks = doc.select("a")

            // Key= plain link ("a")
            // Value = simplified HREF
            val elements: MutableMap<Element, String> = HashMap()
            for (link in plainLinks) {
                if (site.bookCardExcludedParentClasses.isNotEmpty()) {
                    val isForbidden = link.parents().any { e: Element ->
                        containsForbiddenClass(site, e.classNames())
                    }
                    if (isForbidden) continue
                }
                val aHref = simplifyUrl(link.attr("href"))
                elements[link] = aHref
            }
            for (entry in elements.entries) {
                if (site.galleryHeight != -1) {
                    for (blockedTag in blockedTags) {
                        if (entry.value.contains("/tag/") || entry.value.contains("/category/")) {
                            var tag: String? = null
                            if (entry.key.childNodeSize() != 0) tag =
                                entry.key.childNode(0).toString()
                            if (tag == null) break
                            if (blockedTag.equals(
                                    tag, ignoreCase = true
                                ) || isPresentAsWord(blockedTag, tag)
                            ) {
                                var imgParent = entry.key
                                for (i in 0..site.galleryHeight) {
                                    if (imgParent.parent() != null) imgParent =
                                        imgParent.parent()!!
                                }
                                val imgs = imgParent.allElements.select("img")
                                for (img in imgs) {
                                    if (img.parent() != null) img.parent()!!
                                        .addClass("watermarked-blocked")
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
        customHtmlRewriter?.invoke(doc)
        return ByteArrayInputStream(doc.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun containsForbiddenClass(s: Site, classNames: Set<String>): Boolean {
        val forbiddenElements = s.bookCardExcludedParentClasses
        return classNames.any { o -> forbiddenElements.contains(o) }
    }

    // TODO cache assets (not replacements)
    fun getJsScript(
        context: Context,
        assetName: String,
        replacements: Map<String, String>?
    ): String {
        val sb = StringBuilder()
        sb.append("javascript:")
        getAssetAsString(context.assets, assetName, sb)
        var result = sb.toString()
        replacements?.forEach {
            result = result.replace(it.key, it.value)
        }
        return result
    }


    interface CustomWebActivity : WebResultConsumer {
        // ACTIONS
        fun loadUrl(url: String)

        // CALLBACKS
        fun onPageStarted(
            url: String,
            isGalleryPage: Boolean,
            isHtmlLoaded: Boolean,
            isBookmarkable: Boolean
        )

        fun onPageFinished(
            url: String,
            isResultsPage: Boolean,
            isGalleryPage: Boolean
        )

        fun onGalleryPageStarted(url: String = "")

        // GETTERS
        val allSiteUrls: List<String>

        val allMergedBooksUrls: List<String>
        val allQueuedBooksUrls: List<String>
        val prefBlockedTags: List<String>
        val customCss: String
        val scope: CoroutineScope
        val alertStatus: AlertStatus
    }
}