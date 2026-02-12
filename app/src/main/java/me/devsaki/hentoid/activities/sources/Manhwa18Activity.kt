package me.devsaki.hentoid.activities.sources

import android.graphics.Bitmap
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.Manhwa18Content
import timber.log.Timber
import java.io.IOException

class Manhwa18Activity : BaseBrowserActivity() {

    companion object {
        const val GALLERY_PATTERN = "^https://manhwa18.net/manga/[%\\w\\-]+$"

        private const val DOMAIN_FILTER = "manhwa18.net"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "/chap")
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
    }

    override fun getStartSite(): Site {
        return Site.MANHWA18
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = M18WebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)

        // Init fetch handler here for convenience
        fetchHandler = { url: String, body: String -> client.onFetchCall(url, body) }
        return client
    }

    private inner class M18WebClient(
        site: Site,
        filter: Array<String>,
        activity: BrowserActivity
    ) :
        CustomWebViewClient(site, filter, activity) {
        // Flag to only process the first XHR call of a given page
        var loadedNavUrl = ""

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            loadedNavUrl = url
        }

        fun onFetchCall(url: String, body: String) {
            if (!isGalleryPage(url)) return
            try {
                lifecycleScope.launch {
                    var skip = false
                    withContext(Dispatchers.Main) {
                        synchronized(DOMAIN_FILTER) {
                            if (loadedNavUrl != webView.url.toString())
                                loadedNavUrl = webView.url.toString()
                            else skip = true
                        }
                    }
                    if (skip) return@launch
                    val contentParser = Manhwa18Content()
                    try {
                        var content = withContext(Dispatchers.IO) {
                            contentParser.toContent(url)
                        }
                        content = super.processContent(content, content.galleryUrl, false)
                        resConsumer?.onContentReady(content, false)
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }

        // Call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            activity?.onGalleryPageStarted()
            val contentParser: ContentParser = Manhwa18Content()

            lifecycleScope.launch {
                try {
                    var content = withContext(Dispatchers.IO) {
                        contentParser.toContent(url)
                    }
                    content = super.processContent(content, content.galleryUrl, quickDownload)
                    resConsumer?.onContentReady(content, quickDownload)
                } catch (t: Throwable) {
                    Timber.w(t)
                }
            }
            return null
        }
    }
}