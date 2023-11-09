package me.devsaki.hentoid.views

import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.annimon.stream.function.Consumer
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.sources.AnchiraGalleryMetadata
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.HttpHelper
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class AnchiraBackgroundWebView(context: Context, site: Site) : WebView(context) {
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
        addJavascriptInterface(
            AnchiraJsInterface { a -> client.jsHandler(a) },
            "anchiraJsInterface"
        )
    }

    fun loadUrl(url: String, onLoaded: Runnable) {
        client.startLoad(url, onLoaded)
        super.loadUrl(url)
    }

    internal class SingleLoadWebViewClient(private val site: Site) :
        WebViewClient() {
        private var targetUrl: String? = null
        private var onLoaded: Runnable? = null
        private val isPageLoading = AtomicBoolean(false)
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Timber.v(">>> onPageStarted %s", url)
            isPageLoading.set(true)
            view.loadUrl(getJsScript(view.context, "anchira_pages.js"))
        }

        override fun onPageFinished(view: WebView, url: String) {
            Timber.v(">>> onPageFinished %s", url)
            isPageLoading.set(false)
            if (onLoaded != null && targetUrl.equals(url, ignoreCase = true)) onLoaded!!.run()
        }

        fun startLoad(url: String, onLoaded: Runnable) {
            isPageLoading.set(true)
            targetUrl = url
            this.onLoaded = onLoaded
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return shouldInterceptRequestInternal(view, request, site)
        }

        private fun getJsScript(context: Context, assetName: String): String {
            val sb = java.lang.StringBuilder()
            sb.append("javascript:")
            FileHelper.getAssetAsString(context.assets, assetName, sb)
            return sb.toString()
        }

        fun jsHandler(a: AnchiraGalleryMetadata) {
            Timber.d("anchira2 %s", a)
        }
    }

    class AnchiraJsInterface(private val handler: Consumer<AnchiraGalleryMetadata>) {
        @JavascriptInterface
        @Suppress("unused")
        fun transmit(a: String) {
            Timber.d("anchira1 %s", a)
            val data = JsonHelper.jsonToObject(a, AnchiraGalleryMetadata::class.java)
            handler.accept(data)
        }
    }

    companion object {
        fun shouldInterceptRequestInternal(
            view: WebView,
            request: WebResourceRequest,
            site: Site
        ): WebResourceResponse? {
            val url = request.url.toString()
            val uriParts = HttpHelper.UriParts(url)
            if (uriParts.entireFileName.startsWith("app.") && uriParts.extension.equals("js")) {
                val requestHeadersList =
                    HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, url)
                try {
                    // Query resource here, using OkHttp
                    HttpHelper.getOnlineResource(
                        url,
                        requestHeadersList,
                        site.useMobileAgent(),
                        site.useHentoidAgent(),
                        site.useWebviewAgent()
                    ).use { response ->
                        val body = response.body ?: throw IOException("Empty body")
                        if (response.code < 300) {
                            var jsFile = body.source().readString(StandardCharsets.UTF_8)
                            Timber.d("app JS found")

                            val beginStr = "arguments;return new Promise((function("
                            val beginIndex = jsFile.indexOf(beginStr)
                            val endIndex = jsFile.indexOf("(void 0)}))}}")
                            if (beginIndex > -1 && endIndex > -1) {
                                val funStr = "function "
                                val funBeginIndex =
                                    jsFile.indexOf(funStr, beginIndex + beginStr.length)
                                val bracketBeginIndex =
                                    jsFile.indexOf("(", funBeginIndex + funStr.length)
                                val bracketEndIndex = jsFile.indexOf(")", bracketBeginIndex)
                                val argumentName =
                                    jsFile.substring(bracketBeginIndex + 1, bracketEndIndex)

                                val part1 = jsFile.substring(0, bracketEndIndex + 2)
                                val part2 = jsFile.substring(bracketEndIndex + 2)
                                jsFile = "$part1 processAnchiraData($argumentName); $part2"
                            } else {
                                Timber.w("APP JS LOCATION NOT FOUND")
                            }
                            Timber.d("app JS edited")
                            return HttpHelper.okHttpResponseToWebkitResponse(
                                response, ByteArrayInputStream(
                                    jsFile.toByteArray(
                                        StandardCharsets.UTF_8
                                    )
                                )
                            )
                        }
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }
            return sendRequest(request, site)
        }

        // TODO optimize, factorize with the ones in HitomiWv and CustomWebViewClient
        private fun sendRequest(
            request: WebResourceRequest,
            site: Site
        ): WebResourceResponse? {
            if (Preferences.getDnsOverHttps() > -1) {
                // Query resource using OkHttp
                val urlStr = request.url.toString()
                val requestHeadersList =
                    HttpHelper.webkitRequestHeadersToOkHttpHeaders(
                        request.requestHeaders,
                        urlStr
                    )
                try {
                    val response = HttpHelper.getOnlineResource(
                        urlStr,
                        requestHeadersList,
                        site.useMobileAgent(),
                        site.useHentoidAgent(),
                        site.useWebviewAgent()
                    )

                    // Scram if the response is a redirection or an error
                    if (response.code >= 300) return null
                    val body = response.body ?: throw IOException("Empty body")
                    return HttpHelper.okHttpResponseToWebkitResponse(
                        response,
                        body.byteStream()
                    )
                } catch (e: IOException) {
                    Timber.i(e)
                }
            }
            return null
        }
    }
}