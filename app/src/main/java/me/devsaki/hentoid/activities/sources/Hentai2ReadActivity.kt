package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class Hentai2ReadActivity : BaseWebActivity() {

    companion object {
        const val GALLERY_PATTERN = "//hentai2read.com/[\\w\\-]+/$"

        private const val DOMAIN_FILTER = "hentai2read.com"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "[0-9\\.]+/$"))
        private val REMOVABLE_ELEMENTS =
            arrayOf("div[data-refresh]", ".js-rotating") // iframe[src*=ads]

        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
        private val JS_CONTENT_BLACKLIST = arrayOf(
            "exoloader",
            "popunder",
            "trackingurl",
            "exo_slider",
            "exojspop",
            "data-exo",
            "exoslider"
        )
    }


    override fun getStartSite(): Site {
        return Site.HENTAI2READ
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.addJavascriptBlacklist(*JS_CONTENT_BLACKLIST)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        return client
    }
}