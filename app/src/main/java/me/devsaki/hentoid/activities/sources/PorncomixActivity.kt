package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class PorncomixActivity : BaseWebActivity() {

    companion object {
        private val DOMAIN_FILTER = arrayOf(
            "www.porncomixonline.net",
            "www.porncomixonline.com",
            "porncomicszone.net",
            "porncomixinfo.com",
            "porncomixinfo.net",
            "bestporncomix.com",
            "gedecomix.com",
            "allporncomix.net"
        )
        private val GALLERY_FILTER = arrayOf(
            "//www.porncomixonline.(com|net)/(?!m-comic)([\\w\\-]+)/[\\w\\-]+/$",
            "//www.porncomixonline.(com|net)/m-comic/[\\w\\-]+/[\\w\\-]+$",
            "//www.porncomixonline.(com|net)/m-comic/[\\w\\-]+/[\\w\\-]+/$",
            "//www.porncomixonline.(com|net)/xxxtoons/(?!page)[\\w\\-]+/[\\w\\-]+$",
            "//www.porncomixonline.com/(?!m-comic)([\\w\\-]+)/[\\w\\-]+/$",
            "//www.porncomixonline.com/m-comic/[\\w\\-]+/[\\w\\-]+$",
            "//porncomicszone.net/[0-9]+/[\\w\\-]+/[0-9]+/$",
            "//porncomixinfo.(com|net)/manga-comics/[\\w\\-]+/[\\w\\-]+/$",
            "//porncomixinfo.(com|net)/chapter/[\\w\\-]+/[\\w\\-]+/$",
            "//bestporncomix.com/gallery/[\\w\\-]+/$",
            "//gedecomix.com/porncomic/[\\w\\-]+/[\\w\\-]+/$",
            "//allporncomix.net/content/[\\w\\-]+$"
        )
        private val JS_CONTENT_BLACKLIST =
            arrayOf("ai_process_ip_addresses", "adblocksucks", "adblock-proxy-super-secret")
        private val REMOVABLE_ELEMENTS = arrayOf("iframe[name^='spot']")
    }

    override fun getStartSite(): Site {
        return Site.PORNCOMIX
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(*DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.addJavascriptBlacklist(*JS_CONTENT_BLACKLIST)
        client.adBlocker.addToJsUrlWhitelist(*DOMAIN_FILTER)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        return client
    }
}