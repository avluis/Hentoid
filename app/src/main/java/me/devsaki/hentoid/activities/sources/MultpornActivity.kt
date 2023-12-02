package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class MultpornActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "multporn.net"
        private val GALLERY_FILTER = arrayOf(
            "multporn.net/node/[0-9]+$",
            "multporn.net/(hentai_manga|authors|hentai|comics|pictures|rule_6|gay_porn_comics|GIF)/[\\w%_\\-]+$",
            "multporn.net/node/[0-9]+$",
            "multporn.net/(hentai_manga|authors|hentai|comics|pictures|rule_6|gay_porn_comics|GIF)/[\\w%_\\-]+/[\\w%_\\-]+$"
        )
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
        private val JS_CONTENT_BLACKLIST = arrayOf("exoloader", "popunder")
        private val AD_ELEMENTS = arrayOf("iframe", ".c-ads")
    }


    override fun getStartSite(): Site {
        return Site.MULTPORN
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*AD_ELEMENTS)
        client.addJavascriptBlacklist(*JS_CONTENT_BLACKLIST)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        //for (String s : JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s);
        return client
    }
}