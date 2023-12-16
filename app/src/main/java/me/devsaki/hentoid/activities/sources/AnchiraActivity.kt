package me.devsaki.hentoid.activities.sources

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.AnchiraGalleryMetadata
import me.devsaki.hentoid.parsers.images.AnchiraParser
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.HttpHelper.UriParts
import me.devsaki.hentoid.views.AnchiraBackgroundWebView
import me.devsaki.hentoid.views.AnchiraBackgroundWebView.AnchiraJsContentInterface
import me.devsaki.hentoid.views.AnchiraBackgroundWebView.Companion.interfaceName
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class AnchiraActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "anchira.to"
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)

        private val JS_CONTENT_BLACKLIST =
            arrayOf("exoloader", "popunder", "adGuardBase", "adtrace.online", "Admanager")
        private val GALLERY_FILTER = arrayOf("//anchira.to/g/[\\w\\-]+/[\\w\\-]+$")
    }

    override fun getStartSite(): Site {
        return Site.ANCHIRA
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = AnchiraWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        webView.addJavascriptInterface(AnchiraJsContentInterface { s: Content ->
            client.jsHandler(s, false)
        }, interfaceName)
        return client
    }

    class AnchiraWebClient : CustomWebViewClient {
        constructor(
            site: Site,
            galleryUrl: Array<String>,
            resultConsumer: WebResultConsumer,
            jsMode: Int
        ) : super(site, galleryUrl, resultConsumer) {
            if (0 == jsMode) { // Content parsing
                setJsStartupScripts("wysiwyg_parser.js")
                addJsReplacement("\$interface", interfaceName)
                addJsReplacement("\$fun", AnchiraBackgroundWebView.functionName)
            } else { // Images parsing
                setJsStartupScripts("anchira_pages_parser.js")
            }
        }

        internal constructor(
            site: Site,
            galleryUrl: Array<String>,
            activity: CustomWebActivity
        ) : super(site, galleryUrl, activity) {
            setJsStartupScripts("wysiwyg_parser.js")
            addJsReplacement("\$interface", interfaceName)
            addJsReplacement("\$fun", AnchiraBackgroundWebView.functionName)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            val uriParts = UriParts(url, true)
            if (uriParts.entireFileName.startsWith("app.") && uriParts.extension == "js") {
                try {
                    HttpHelper.getOnlineResourceFast(
                        url,
                        HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, url),
                        Site.ANCHIRA.useMobileAgent(),
                        Site.ANCHIRA.useHentoidAgent(),
                        Site.ANCHIRA.useWebviewAgent()
                    ).use { response ->
                        // Scram if the response is a redirection or an error
                        if (response.code >= 300) return null

                        // Scram if the response is empty
                        val body = response.body ?: throw IOException("Empty body")
                        var jsFile =
                            body.source().readString(StandardCharsets.UTF_8)
                        jsFile = jsFile.replace(".isTrusted", ".cancelable")
                        return HttpHelper.okHttpResponseToWebkitResponse(
                            response, ByteArrayInputStream(
                                jsFile.toByteArray(StandardCharsets.UTF_8)
                            )
                        )
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }
            if (url.contains(AnchiraGalleryMetadata.IMG_HOST)) {
                val parts = url.split("/")
                if (parts.size > 7) {
                    // TODO that's ugly; find a more suitable interface; e.g. onImagesReady
                    val c = Content()
                    c.site = Site.ANCHIRA
                    c.coverImageUrl = url
                    resConsumer.onContentReady(c, false)

                    // Kill CORS
                    if (request.method.equals("options", ignoreCase = true)) {
                        try {
                            val response = HttpHelper.optOnlineResourceFast(
                                url,
                                HttpHelper.webkitRequestHeadersToOkHttpHeaders(
                                    request.requestHeaders,
                                    url
                                ),
                                Site.ANCHIRA.useMobileAgent(),
                                Site.ANCHIRA.useHentoidAgent(),
                                Site.ANCHIRA.useWebviewAgent()
                            )

                            // Scram if the response is a redirection or an error
                            if (response.code >= 300) return null

                            // Scram if the response is empty
                            val body = response.body ?: throw IOException("Empty body")
                            return HttpHelper.okHttpResponseToWebkitResponse(
                                response,
                                body.byteStream()
                            )
                        } catch (e: Exception) {
                            Timber.w(e)
                        }
                    }
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun parseResponse(
            urlStr: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            // Complete override of default behaviour because
            // - There's no HTML to be parsed for ads
            // - The interesting parts are loaded by JS, not now
            if (quickDownload) {
                // Use a background Wv to get book attributes when targeting another page (quick download)
                val parser = AnchiraParser()
                try {
                    val content = parser.parseContentWithWebview(urlStr)
                    content.status = StatusContent.SAVED
                    activity?.onGalleryPageStarted()
                    val contentFinal = super.processContent(content, urlStr, quickDownload)
                    val handler = Handler(Looper.getMainLooper())
                    handler.post { resConsumer.onContentReady(contentFinal, true) }
                } catch (e: Exception) {
                    Timber.w(e)
                } finally {
                    parser.clear()
                }
            }
            return null
        }

        fun jsHandler(content: Content, quickDownload: Boolean) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                processContent(content, content.galleryUrl, quickDownload)
                resConsumer.onContentReady(content, quickDownload)
            }
        }
    }
}