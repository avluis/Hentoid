package me.devsaki.hentoid.activities.sources

import android.net.Uri
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.retrofit.DeviantArtServer
import me.devsaki.hentoid.util.network.HttpHelper
import timber.log.Timber
import java.io.IOException

class DeviantArtActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = ".deviantart.com"
        private val GALLERY_FILTER = arrayOf(
            "deviantart.com/[\\w\\-]+/gallery/",  // User gallery; multiple suffixes may be added
            "deviantart.com/[\\w\\-]+/art/[\\w\\-]+",  // Art page
            "deviantart.com/_puppy/dadeviation/init\\?deviationid=" // Art page loaded using XHR call
            /*
            "pixiv.net/touch/ajax/illust/series_content/",  // Manga/series page (anthology) / load using fetch call
            "pixiv.net/touch/ajax/user/details\\?",  // User page / load using fetch call
            "pixiv.net/[\\w\\-]+/artworks/[0-9]+$",  // Illustrations page (single gallery)
            "pixiv.net/user/[0-9]+/series/[0-9]+$",  // Manga/series page (anthology)
            "pixiv.net/users/[0-9]+$" // User page
             */
        )
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)

        private val NAVIGATION_QUERIES =
            arrayOf("/details?", "/search/illusts?", "ajax/pages/top?", "/tag_stories?")
    }


    override fun getStartSite(): Site {
        return Site.DEVIANTART
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = DeviantArtWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)

        xhrHandler = { url: String, body: String -> client.onXhrCall(url, body) }
        return client
    }

    private inner class DeviantArtWebClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) :
        CustomWebViewClient(site, filter, activity) {
        fun onXhrCall(url: String, body: String) {
            // TODO only react to the very first call for every page load
            if (!isGalleryPage(url)) return
            try {
                val uri = Uri.parse(url)
                val cookieStr = HttpHelper.getCookies(
                    url,
                    null,
                    getStartSite().useMobileAgent(),
                    getStartSite().useHentoidAgent(),
                    getStartSite().useHentoidAgent()
                )
                val call = DeviantArtServer.API.getDeviation(
                    uri.getQueryParameter("deviationid") ?: "",
                    uri.getQueryParameter("username") ?: "",
                    uri.getQueryParameter("type") ?: "",
                    uri.getQueryParameter("include_session") ?: "",
                    uri.getQueryParameter("csrf_token") ?: "",
                    uri.getQueryParameter("expand") ?: "",
                    uri.getQueryParameter("da_minor_version") ?: "",
                    cookieStr,
                    ParseHelper.getUserAgent(Site.DEVIANTART)
                )
                val response = call.execute()
                if (response.isSuccessful) {
                    response.body()?.apply {
                        Timber.v("title=${deviation.title}")
                    }
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }

        /*
        // Call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            activity?.onGalleryPageStarted()
            val contentParser: ContentParser = DeviantArtContent()

            lifecycleScope.launch {
                try {
                    var content = withContext(Dispatchers.IO) {
                        contentParser.toContent(url)
                    }
                    content = super.processContent(content, content.galleryUrl, quickDownload)
                    resConsumer.onContentReady(content, quickDownload)
                } catch (t: Throwable) {
                    Timber.w(t)
                }
            }
            return null
        }
         */
    }
}