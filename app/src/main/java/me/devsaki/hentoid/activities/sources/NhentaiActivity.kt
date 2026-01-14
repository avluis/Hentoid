package me.devsaki.hentoid.activities.sources

import android.net.Uri
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.network.parseParameters

class NhentaiActivity : BaseBrowserActivity() {
    companion object {
        private const val DOMAIN_FILTER = "nhentai.net"
        const val FAVS_FILTER = "$DOMAIN_FILTER/favorites/"
        private val GALLERY_FILTER =
            arrayOf(
                "$DOMAIN_FILTER/g/[%0-9]+[/]{0,1}$",
                "$DOMAIN_FILTER/search/\\?q=[%0-9]+$",
                FAVS_FILTER
            )
        private val RESULTS_FILTER = arrayOf(
            "//$DOMAIN_FILTER[/]*$",
            "//$DOMAIN_FILTER/\\?",
            "//$DOMAIN_FILTER/search/\\?",
            "//$DOMAIN_FILTER/(character|artist|parody|tag|group)/"
        )
        private val BLOCKED_CONTENT = arrayOf("popunder")
        private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement")
    }

    override fun getStartSite(): Site {
        return Site.NHENTAI
    }


    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.setResultsUrlPatterns(*RESULTS_FILTER)
        client.setResultUrlRewriter { resultsUri: Uri, page: Int ->
            rewriteResultsUrl(resultsUri, page)
        }
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }

    private fun rewriteResultsUrl(resultsUri: Uri, page: Int): String {
        val builder = resultsUri.buildUpon()
        val params = parseParameters(resultsUri).toMutableMap()
        params["page"] = page.toString() + ""
        builder.clearQuery()
        params.forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }
        return builder.toString()
    }
}