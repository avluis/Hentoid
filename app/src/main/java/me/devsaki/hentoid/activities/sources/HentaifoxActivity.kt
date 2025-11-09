package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "hentaifox.com"
private val GALLERY_FILTER = arrayOf("hentaifox.com/gallery/[0-9]+/$")
private val REMOVABLE_ELEMENTS = arrayOf(".bblocktop")

class HentaifoxActivity : BaseBrowserActivity() {
    override fun getStartSite(): Site {
        return Site.HENTAIFOX
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }
}