package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class HdPornComicsActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "hdporncomics.com"
        private val GALLERY_FILTER = arrayOf("hdporncomics.com/[\\w\\-]+/$")
    }

    override fun getStartSite(): Site {
        return Site.HDPORNCOMICS
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }
}