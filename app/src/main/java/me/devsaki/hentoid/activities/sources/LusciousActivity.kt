package me.devsaki.hentoid.activities.sources

import android.webkit.WebResourceResponse
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.json.sources.luscious.LusciousQuery
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.LusciousContent
import me.devsaki.hentoid.util.jsonToObject
import timber.log.Timber
import java.io.IOException

class LusciousActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "luscious.net"
        val GALLERY_FILTER = arrayOf(
            "operationName=AlbumGet",  // Fetch using GraphQL call
            "luscious.net/[\\w\\-]+/[\\w\\-]+_[0-9]+/$" // Actual gallery page URL (NB : only works for the first viewed gallery, or when manually reloading a page)
        )
        //private static final String[] REMOVABLE_ELEMENTS = {".ad_banner"}; <-- doesn't work; added dynamically on an element tagged with a neutral-looking class
    }

    override fun getStartSite(): Site {
        return Site.LUSCIOUS
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = LusciousWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        client.setJsStartupScripts("luscious_adblock.js")

        // Init fetch handler here for convenience
        fetchHandler = { url: String, body: String -> client.onFetchCall(url, body) }
        return client
    }

    private inner class LusciousWebClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) :
        CustomWebViewClient(site, filter, activity) {
        fun onFetchCall(url: String, body: String) {
            if (!isGalleryPage(url)) return
            try {
                jsonToObject(body, LusciousQuery::class.java)?.let { query ->
                    val id = query.getIdVariable()
                    if (id.isNotEmpty()) parseResponse(
                        id, null,
                        analyzeForDownload = true,
                        quickDownload = false
                    )
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
            val contentParser: ContentParser = LusciousContent()

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