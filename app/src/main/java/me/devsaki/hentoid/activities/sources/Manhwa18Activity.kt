package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class Manhwa18Activity : BaseWebActivity() {

    companion object {
        const val GALLERY_PATTERN = "//manhwa18.net/manga/[%\\w\\-]+$"

        private const val DOMAIN_FILTER = "manhwa18.net"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "/chap")
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
    }

    override fun getStartSite(): Site {
        return Site.MANHWA18
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        return client
    }
}