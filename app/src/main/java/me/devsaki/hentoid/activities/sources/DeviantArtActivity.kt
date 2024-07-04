package me.devsaki.hentoid.activities.sources

import android.graphics.Bitmap
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.DeviantArtContent
import timber.log.Timber
import java.io.IOException

class DeviantArtActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = ".deviantart.com"
        private val GALLERY_FILTER = arrayOf(
            "deviantart.com/[\\w\\-]+/art/[\\w\\-]+",  // Deviation page
            "deviantart.com/[\\w\\-]+/gallery$",  // User gallery page; mobile version only contains the first 10 deviations and uses XHR for dynamically loading the rest
            "deviantart.com/_puppy/dadeviation/init\\?", // Art page info loaded using XHR call
            "deviantart.com/_puppy/dashared/gallection/contents\\?", // User gallery info loaded using XHR call
            "deviantart.com/_puppy/dauserprofile/init/gallery\\?" // User info loaded using XHR call
        )
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
    }


    override fun getStartSite(): Site {
        return Site.DEVIANTART
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = DeviantArtWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)

        xhrHandler = { url: String, _: String -> client.onXhrCall(url) }
        return client
    }

    private inner class DeviantArtWebClient(
        site: Site, filter: Array<String>, activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        // Flag to only process the first XHR call of a given page
        var loadedNavUrl = ""

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            loadedNavUrl = url
        }

        fun onXhrCall(url: String) {
            if (!isGalleryPage(url)) return
            try {
                lifecycleScope.launch {
                    var skip = false
                    withContext(Dispatchers.Main) {
                        synchronized(DOMAIN_FILTER) {
                            if (loadedNavUrl != webView.url.toString()) loadedNavUrl =
                                webView.url.toString()
                            else skip = true
                        }
                    }
                    if (skip) return@launch
                    val contentParser = DeviantArtContent()
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

        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            return if (url.contains("/_puppy/")) null // Don't process XHR calls
            else super.parseResponse(url, requestHeaders, analyzeForDownload, quickDownload)
        }
    }
}