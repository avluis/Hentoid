package me.devsaki.hentoid.views

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.annimon.stream.function.Consumer
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.activities.sources.AnchiraActivity.AnchiraWebClient
import me.devsaki.hentoid.activities.sources.WebResultConsumer
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.sources.AnchiraGalleryMetadata
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.HttpHelper
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class AnchiraBackgroundWebView(context: Context, consumer: WebResultConsumer, site: Site) :
    WebView(context) {
    private val client: AnchiraWebClient

    init {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(this, true)
        val webSettings = settings
        webSettings.userAgentString = site.userAgent
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        client = AnchiraWebClient(site, consumer)
        webViewClient = client

        addJavascriptInterface(AnchiraJsInterface { s ->
            client.jsHandler(s, false)
        }, "anchiraJsInterface")
    }

    class AnchiraJsInterface(private val handler: Consumer<AnchiraGalleryMetadata>) {
        @JavascriptInterface
        @Suppress("unused")
        fun transmit(a: String) {
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
            return null
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