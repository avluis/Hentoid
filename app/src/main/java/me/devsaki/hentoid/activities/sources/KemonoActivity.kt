package me.devsaki.hentoid.activities.sources

import android.webkit.WebResourceResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.KemonoContent
import timber.log.Timber

class KemonoActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = "kemono.su"
        private val GALLERY_FILTER = arrayOf(
            "kemono.su/[\\w_%\\-]+/user/[\\d\\-]+$",
            "kemono.su/api/v1/[\\w_%\\-]+/user/[\\d\\-]+/posts-legacy$",
            "kemono.su/[\\w_%\\-]+/user/[\\d\\-]+/post/[\\d\\-]+$",
            "kemono.su/api/v1/[\\w_%\\-]+/user/[\\d\\-]+/post/[\\d\\-]+$"
        )
        private val BLOCKED_CONTENT = arrayOf("popunder")
        private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement")
    }

    override fun getStartSite(): Site {
        return Site.KEMONO
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = KemonoViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }

    private inner class KemonoViewClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        // We call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            if (analyzeForDownload || quickDownload) {
                activity?.onGalleryPageStarted()
                val contentParser: ContentParser = KemonoContent()

                lifecycleScope.launch {
                    try {
                        var content = withContext(Dispatchers.IO) {
                            contentParser.toContent(url)
                        }
                        content = super.processContent(content, url, quickDownload)
                        resConsumer?.onContentReady(content, quickDownload)
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            }
            return if (isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags() || isMarkQueued())
                super.parseResponse(
                    url,
                    requestHeaders,
                    analyzeForDownload = false,
                    quickDownload = false
                ) // Rewrite HTML
            else null
        }
    }
}