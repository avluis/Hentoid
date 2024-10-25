package me.devsaki.hentoid.util.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.core.CLOUDFLARE_COOKIE
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.getFixedContext
import me.devsaki.hentoid.util.pause
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class CloudflareHelper {

    companion object {
        private const val RELOAD_LIMIT = 3
    }

    private var webView: CloudflareWebView? = null
    private val stopped = AtomicBoolean(false)

    init {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            this.webView = try {
                CloudflareWebView(HentoidApp.getInstance())
            } catch (e: Resources.NotFoundException) {
                // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
                // Creating with the application Context fixes this, but is not generally recommended for view creation
                CloudflareWebView(getFixedContext(HentoidApp.getInstance()))
            }
        }
    }


    // TODO doc
    fun tryPassCloudflare(
        revivedSite: Site,
        oldCookie: String?
    ): Boolean {
        val oldCookieInternal: String =
            oldCookie ?: parseCookies(getCookies(revivedSite.url))[CLOUDFLARE_COOKIE] ?: ""

        // Nuke the cookie to force its refresh
        val domain = "." + getDomainFromUri(revivedSite.url)
        setCookies(domain, "$CLOUDFLARE_COOKIE=;Max-Age=0; secure; HttpOnly")

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            webView?.setUserAgent(revivedSite.userAgent)
            webView?.setAgentProperties(
                revivedSite.useMobileAgent,
                revivedSite.useHentoidAgent,
                revivedSite.useWebviewAgent
            )
            webView?.loadUrl(revivedSite.url)
        }
        var reloadTimer = 0
        var reloadCounter = 0
        var passed = false
        // Wait for cookies to refresh
        do {
            val cfcookie =
                parseCookies(getCookies(revivedSite.url))[CLOUDFLARE_COOKIE]
            if (!cfcookie.isNullOrEmpty() && cfcookie != oldCookieInternal) {
                Timber.d("CF-COOKIE : refreshed !")
                passed = true
            } else {
                Timber.v("CF-COOKIE : not refreshed")
                // Reload if nothing for 7.5s
                reloadTimer++
                if (reloadTimer > 5 && reloadCounter < RELOAD_LIMIT) {
                    reloadTimer = 0
                    reloadCounter++
                    Timber.v("CF-COOKIE : RELOAD %d/%d", reloadCounter, RELOAD_LIMIT)
                    handler.post {
                        webView?.reload()
                    }
                }
            }
            // We're polling the DB because we can't observe LiveData from a background service
            pause(1500)
        } while (reloadCounter < RELOAD_LIMIT && !passed && !stopped.get())

        return passed
    }

    fun clear() {
        stopped.set(true)
        webView?.removeAllViews()
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            webView?.destroy()
        }
        webView = null
    }

    class CloudflareProtectedException : Exception()

    @SuppressLint("SetJavaScriptEnabled")
    internal class CloudflareWebView constructor(context: Context) : WebView(context) {

        val client: CloudflareWebViewClient

        init {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptThirdPartyCookies(this, true)
            settings.let {
                it.builtInZoomControls = true
                it.displayZoomControls = false
                it.domStorageEnabled = true
                it.useWideViewPort = true
                it.javaScriptEnabled = true
                it.loadWithOverviewMode = true
            }
            if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
            client = CloudflareWebViewClient(Preferences.getDnsOverHttps() > -1)
            webViewClient = client
        }

        fun setUserAgent(agent: String) {
            settings.userAgentString = agent
        }

        fun setAgentProperties(
            useMobileAgent: Boolean,
            useHentoidAgent: Boolean,
            useWebviewAgent: Boolean
        ) {
            client.useMobileAgent = useMobileAgent
            client.useHentoidAgent = useHentoidAgent
            client.useWebviewAgent = useWebviewAgent
        }
    }

    internal class CloudflareWebViewClient(private val dnsOverHttpsEnabled: Boolean) :
        WebViewClient() {

        var useMobileAgent = false
        var useHentoidAgent = false
        var useWebviewAgent = false

        /**
         * Note : this method is called by a non-UI thread
         */
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (dnsOverHttpsEnabled) {
                // Query resource using OkHttp
                val urlStr = request.url.toString()
                val requestHeadersList =
                    webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, urlStr)
                try {
                    val response = getOnlineResource(
                        urlStr,
                        requestHeadersList,
                        useMobileAgent,
                        useHentoidAgent,
                        useWebviewAgent
                    )

                    // Scram if the response is a redirection or an error
                    if (response.code >= 300) return null
                    val body = response.body ?: throw IOException("Empty body")
                    return okHttpResponseToWebkitResponse(response, body.byteStream())
                } catch (e: IOException) {
                    Timber.i(e)
                }
            }
            return null
        }
    }
}