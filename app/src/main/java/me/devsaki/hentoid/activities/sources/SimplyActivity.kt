package me.devsaki.hentoid.activities.sources

import android.os.Build
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.SimplyApiContent
import timber.log.Timber

class SimplyActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "simply-hentai.com"
        val GALLERY_FILTER = arrayOf(
            "simply-hentai.com/[%\\w\\-]+/[%\\w\\-]+$",
            "api.simply-hentai.com/v3/[%\\w\\-]+/[%\\w\\-]+$"
        )
    }


    override fun getStartSite(): Site {
        return Site.SIMPLY
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = SimplyViewClient(getStartSite(), GALLERY_FILTER, this, webView)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }


    private inner class SimplyViewClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity,
        webView: WebView
    ) : CustomWebViewClient(site, filter, activity) {
        private var swClient: SimplyViewSwClient? = null

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val swController = ServiceWorkerController.getInstance()
                swClient = SimplyViewSwClient(this, webView)
                swController.setServiceWorkerClient(swClient)
            }
        }

        override fun destroy() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                swClient?.destroy()
                swClient = null
            }
            super.destroy()
        }

        // Call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            if (!url.endsWith("/status") && !url.endsWith("/home") && !url.endsWith("/starting")) {
                if (url.contains("api.simply-hentai.com") && (analyzeForDownload || quickDownload)) {
                    activity?.onGalleryPageStarted()
                    val contentParser: ContentParser = SimplyApiContent()

                    lifecycleScope.launch {
                        try {
                            var content = withContext(Dispatchers.IO) {
                                contentParser.toContent(url)
                            }
                            content =
                                super.processContent(content, content.galleryUrl, quickDownload)
                            resConsumer.onContentReady(content, quickDownload)
                        } catch (t: Throwable) {
                            Timber.w(t)
                        }
                    }
                    return null
                }
                // If calls something else than the API, use the standard parseResponse
                return super.parseResponse(
                    url,
                    requestHeaders,
                    analyzeForDownload,
                    quickDownload
                )
            }
            return null
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    private class SimplyViewSwClient(client: CustomWebViewClient, webView: WebView) :
        ServiceWorkerClient() {
        private var webClient: CustomWebViewClient?
        private var webView: WebView?

        init {
            webClient = client
            this.webView = webView
        }

        fun destroy() {
            webClient = null
            webView = null
        }

        override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
            return if (webClient != null)
                webClient!!.shouldInterceptRequest(webView!!, request)
            else null
        }
    }
}