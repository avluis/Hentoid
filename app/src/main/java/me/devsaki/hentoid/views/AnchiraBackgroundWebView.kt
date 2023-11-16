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
import me.devsaki.hentoid.util.exception.ParseException
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
        client = AnchiraWebClient(site, emptyArray(), consumer)
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

                            val landmarkStr = "404==="
                            val borderStr1 = ".dirty"
                            var landmarkIndex = 0
                            var borderIndex1 = 0
                            var found = false

                            while (!found && landmarkIndex > -1) {
                                landmarkIndex = jsFile.indexOf(landmarkStr, landmarkIndex + 1)
                                if (landmarkIndex > -1) {
                                    borderIndex1 = jsFile.indexOf(borderStr1, landmarkIndex)
                                    if (borderIndex1 - landmarkIndex < 300) found = true
                                }
                            }
                            if (!found) throw ParseException("Error while parsing JS : landmark not found")

                            val anchorBeginIndex = jsFile.lastIndexOf("await", landmarkIndex)
                            if (anchorBeginIndex > -1) {
                                val anchorEndIndex = jsFile.indexOf(");", anchorBeginIndex)
                                if (anchorEndIndex > landmarkIndex) throw ParseException("Error while parsing JS : anchor not found")

                                var variableEndIndex = jsFile.indexOf("[", borderIndex1)
                                val variableBeginIndex =
                                    jsFile.lastIndexOf("return ", variableEndIndex)
                                variableEndIndex = jsFile.indexOf("?", variableBeginIndex)
                                val variable =
                                    jsFile.substring(variableBeginIndex + 7, variableEndIndex)

                                val part1 = jsFile.substring(0, anchorEndIndex + 2)
                                val part2 = jsFile.substring(anchorEndIndex + 2)
                                jsFile = "$part1 processAnchiraData($variable); $part2"
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
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
            return null
        }
    }
}