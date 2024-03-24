package me.devsaki.hentoid.views

import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.getOnlineResource
import me.devsaki.hentoid.util.network.okHttpResponseToWebkitResponse
import me.devsaki.hentoid.util.network.webkitRequestHeadersToOkHttpHeaders
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class HitomiBackgroundWebView(context: Context, site: Site) : WebView(context) {
    private var client: SingleLoadWebViewClient

    init {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(this, true)
        val webSettings = settings
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.userAgentString = site.userAgent
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        client = SingleLoadWebViewClient(site)
        webViewClient = client
    }

    fun loadUrl(url: String, onLoaded: Runnable) {
        client.startLoad(url, onLoaded)
        super.loadUrl(url)
    }

    internal class SingleLoadWebViewClient(private val site: Site) :
        WebViewClient() {
        private var targetUrl: String = ""
        private var onLoaded: Runnable? = null
        private val isPageLoading = AtomicBoolean(false)
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Timber.v(">>> onPageStarted %s", url)
            isPageLoading.set(true)
        }

        override fun onPageFinished(view: WebView, url: String) {
            Timber.v(">>> onPageFinished %s", url)
            isPageLoading.set(false)
            if (targetUrl.equals(url, ignoreCase = true)) onLoaded?.run()
        }

        fun startLoad(url: String, onLoaded: Runnable) {
            isPageLoading.set(true)
            targetUrl = url
            this.onLoaded = onLoaded
        }

        // Disable window width checks when the Webview in run outside of the UI
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            if (url.contains("gg.js")) {
                val requestHeadersList =
                    webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, url)
                try {
                    // Query resource here, using OkHttp
                    val response = getOnlineResource(
                        url,
                        requestHeadersList,
                        site.useMobileAgent(),
                        site.useHentoidAgent(),
                        site.useWebviewAgent()
                    )
                    val body = response.body ?: throw IOException("Empty body")
                    if (response.code < 300) {
                        var jsFile = body.source().readString(StandardCharsets.UTF_8)
                        jsFile = jsFile.replace(
                            "\\{[\\s]*return[\\s]+[0-9]+;[\\s]*\\}".toRegex(),
                            "{return o;}"
                        )
                        return okHttpResponseToWebkitResponse(
                            response, ByteArrayInputStream(
                                jsFile.toByteArray(
                                    StandardCharsets.UTF_8
                                )
                            )
                        )
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }
            return sendRequest(request)
        }

        // TODO optimize, factorize
        private fun sendRequest(request: WebResourceRequest): WebResourceResponse? {
            if (Preferences.getDnsOverHttps() > -1) {
                // Query resource using OkHttp
                val urlStr = request.url.toString()
                val requestHeadersList =
                    webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, urlStr)
                try {
                    val response = getOnlineResource(
                        urlStr,
                        requestHeadersList,
                        site.useMobileAgent(),
                        site.useHentoidAgent(),
                        site.useWebviewAgent()
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