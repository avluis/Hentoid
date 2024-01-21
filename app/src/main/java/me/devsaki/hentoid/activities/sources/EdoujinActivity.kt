package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class EdoujinActivity : BaseWebActivity() {
    companion object {
        const val GALLERY_PATTERN = "edoujin.net/manga/[\\w\\-_%]+/$"

        private const val DOMAIN_FILTER = "edoujin.net"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("/$", "\\-") + "[0-9]+/$")

        private val REMOVABLE_ELEMENTS = arrayOf("\$x//*[@class=\"adblock_title\"]/../../../../..")

        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
        private val JS_CONTENT_BLACKLIST = arrayOf("exoloader", "popunder")
    }

    override fun getStartSite(): Site {
        return Site.EDOUJIN
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.addJavascriptBlacklist(*JS_CONTENT_BLACKLIST)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        return client
    }
}