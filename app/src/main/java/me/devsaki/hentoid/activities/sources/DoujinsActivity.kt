package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class DoujinsActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = "doujins.com"
        private val GALLERY_FILTER = arrayOf("//doujins.com/[\\w\\-]+/[\\w\\-]+-[0-9]+")
    }

    override fun getStartSite(): Site {
        return Site.DOUJINS
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        return client
    }
}