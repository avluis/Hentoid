package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class ImhentaiActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = "imhentai.xxx"
        private val GALLERY_FILTER = arrayOf("//imhentai.xxx/gallery/")
        private val REMOVABLE_ELEMENTS = arrayOf(".bblocktop", ".er_container", "#slider")
    }

    override fun getStartSite(): Site {
        return Site.IMHENTAI
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }
}