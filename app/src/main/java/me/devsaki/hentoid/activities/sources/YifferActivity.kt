package me.devsaki.hentoid.activities.sources

import android.webkit.WebResourceResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.YifferContent
import me.devsaki.hentoid.util.network.fetchBodyFast
import timber.log.Timber
import java.io.IOException

private const val DOMAIN_FILTER = "yiffer.xyz"
private const val GALLERY_NAME = "c/[%'\\w\\-_\\.\\!\\,]+"
private val GALLERY_FILTER = arrayOf(
    "$DOMAIN_FILTER/$GALLERY_NAME$",
    "$DOMAIN_FILTER/$GALLERY_NAME\\.data$",
)
private val REMOVABLE_ELEMENTS =
    arrayOf($$"$x//a[contains(@href,\"kkbr.ai\")]/..", "a[href*='https://fundownun']")
//private val JS_URL_PATTERN_WHITELIST = arrayOf("//$DOMAIN_FILTER/")
//private val JS_CONTENT_BLACKLIST = arrayOf("fam-ad.com")

class YifferActivity : BaseBrowserActivity() {

    override fun getStartSite(): Site {
        return Site.YIFFER
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = YifferWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
//        for (s in JS_URL_PATTERN_WHITELIST) client.adBlocker.addJsUrlPatternWhitelist(s)
//        client.addJsContentBlacklist(*JS_CONTENT_BLACKLIST)

//        fetchHandler = { url: String, body: String -> client.onFetchCall(url, body) }

        return client
    }

    private inner class YifferWebClient(
        site: Site,
        filter: Array<String>,
        activity: BrowserActivity
    ) : CustomWebViewClient(site, filter, activity) {

        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            return if (url.endsWith(".data")) {
                onData(url)
                null
            } else super.parseResponse(url, requestHeaders, analyzeForDownload, quickDownload)
        }

        private fun onData(url: String) {
            Timber.d("onData $url")
            try {
                lifecycleScope.launch {
                    var content = Content()
                    try {
                        withContext(Dispatchers.IO) {
                            fetchBodyFast(url, getStartSite()).first?.let { body ->
                                YifferContent.updateFromData(
                                    body.string().replace("\\\"", "'"),
                                    content,
                                    url.substringBeforeLast(".data"),
                                    updateImages = true,
                                    fromHtml = false
                                )
                                content = super.processContent(content, content.galleryUrl, false)
                                resConsumer?.onContentReady(content, false)
                            }
                        }
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
    }
}