package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

const val PCX_GALLERY_PATTERN = "//porncomix.online/[\\w\\-]+/[%\\w\\-]+/$"

private val DOMAIN_FILTER = arrayOf(
    "porncomix.online",
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
    PCX_GALLERY_PATTERN,
    "//porncomix.online/(?!comic)([\\w\\-]+)/[\\w\\-]+/$",
    "//porncomix.online/comic/[\\w\\-]+/[\\w\\-]+$",
    "//porncomix.online/comic/[\\w\\-]+/[\\w\\-]+/$",
    "//porncomix.online/xxxtoons/(?!page)[\\w\\-]+/[\\w\\-]+$",
    "//porncomix.online/(?!comic)([\\w\\-]+)/[\\w\\-]+/$",
    "//porncomix.online/comic/[\\w\\-]+/[\\w\\-]+$",
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

class PorncomixActivity : BaseWebActivity() {

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