package me.devsaki.hentoid.activities.sources

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.PixivContent
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import me.devsaki.hentoid.util.network.okHttpResponseToWebkitResponse
import me.devsaki.hentoid.util.network.webkitRequestHeadersToOkHttpHeaders
import timber.log.Timber
import java.io.IOException

class PixivActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = ".pixiv.net"
        private val GALLERY_FILTER = arrayOf(
            "pixiv.net/touch/ajax/illust/details\\?",  // Illustrations page (single gallery) / load using fetch call
            "pixiv.net/touch/ajax/illust/series_content/",  // Manga/series page (anthology) / load using fetch call
            "pixiv.net/touch/ajax/user/details\\?",  // User page / load using fetch call
            "pixiv.net/[\\w\\-]+/artworks/[0-9]+$",  // Illustrations page (single gallery)
            "pixiv.net/user/[0-9]+/series/[0-9]+$",  // Manga/series page (anthology)
            "pixiv.net/users/[0-9]+$" // User page
        )
        private val BLOCKED_CONTENT = arrayOf("ads-pixiv.net")
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)

        private val NAVIGATION_QUERIES =
            arrayOf("/details?", "/search/illusts?", "ajax/pages/top?", "/tag_stories?")
    }


    override fun getStartSite(): Site {
        return Site.PIXIV
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = PixivWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        client.setJsStartupScripts("pixiv.js")
        webView.addJavascriptInterface(PixivJsInterface(), "pixivJsInterface")
        return client
    }

    override fun onPageStarted(
        url: String,
        isGalleryPage: Boolean,
        isHtmlLoaded: Boolean,
        isBookmarkable: Boolean
    ) {
        super.onPageStarted(url, isGalleryPage, isHtmlLoaded, isBookmarkable)
        binding?.swipeContainer?.isEnabled = true
    }

    override fun onGalleryPageStarted(url: String) {
        super.onGalleryPageStarted(url)
        runOnUiThread {
            binding?.swipeContainer?.isEnabled = false
        }
    }

    private inner class PixivWebClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()

            // Kill CORS
            if (url.contains("s.pximg.net")) {
                try {
                    val response = getOnlineResourceFast(
                        url,
                        webkitRequestHeadersToOkHttpHeaders(request.requestHeaders, url),
                        Site.PIXIV.useMobileAgent,
                        Site.PIXIV.useHentoidAgent,
                        Site.PIXIV.useWebviewAgent
                    )

                    // Scram if the response is a redirection or an error
                    if (response.code >= 300) return null

                    // Scram if the response is empty
                    val body = response.body
                    return okHttpResponseToWebkitResponse(response, body.byteStream())
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }

            // Gray out the action button after every navigation action
            for (s in NAVIGATION_QUERIES) if (url.contains(s)) {
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    activity?.onPageStarted(
                        url,
                        isGalleryPage(url),
                        isHtmlLoaded = false,
                        isBookmarkable = false
                    )
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        // Call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            if (analyzeForDownload || quickDownload) {
                activity?.onGalleryPageStarted()
                if (BuildConfig.DEBUG) Timber.v("WebView : parseResponse Pixiv %s", url)
                val contentParser: ContentParser = PixivContent()

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
            }
            return null
        }
    }

    inner class PixivJsInterface {
        @get:Suppress("unused")
        @get:JavascriptInterface
        val pixivCustomCss: String
            get() = customCss

        @JavascriptInterface
        @Suppress("unused")
        fun isMarkable(bookId: String): Int {
            val downloadedBooks: List<String> = allSiteUrls
            val mergedBooks: List<String> = allMergedBooksUrls
            return if (downloadedBooks.contains(bookId)) 1 else if (mergedBooks.contains(bookId)) 2 else 0
        }
    }
}