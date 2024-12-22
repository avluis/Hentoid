package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class TmoActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = "tmohentai.com"
        private val GALLERY_FILTER = arrayOf("tmohentai.com/contents/[\\w\\-]+[/]{0,1}$")
        private val BLOCKED_CONTENT = arrayOf("popunder")
        private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement")
    }

    override fun getStartSite(): Site {
        return Site.TMO
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }
}