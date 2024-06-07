package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "pururin.to"
const val PUR_GALLERY_PATTERN = "//pururin.to/browse/tags/collection/"
private val GALLERY_FILTER = arrayOf(PUR_GALLERY_PATTERN, "//pururin.to/gallery/")

class PururinActivity : BaseWebActivity() {

    override fun getStartSite(): Site {
        return Site.PURURIN
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        return client
    }
}