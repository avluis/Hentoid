package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class PururinActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "pururin.to"
        private val GALLERY_FILTER = arrayOf("//pururin.to/gallery/")
    }

    override fun getStartSite(): Site {
        return Site.PURURIN
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        return client
    }
}