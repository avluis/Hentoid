package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

private const val DOMAIN_FILTER = "eromanga-sora.com"
private val GALLERY_FILTER = arrayOf("$DOMAIN_FILTER/[%\\w\\-_]+/[\\d]+/$")
private val REMOVABLE_ELEMENTS = arrayOf("#spwrapper", "#extra.column")
private val JS_URL_PATTERN_WHITELIST = arrayOf("//$DOMAIN_FILTER/")
private val JS_CONTENT_BLACKLIST = arrayOf("fam-ad.com")

class EromangaActivity : BaseBrowserActivity() {

    init {
        interceptServiceWorker = true
    }

    override fun getStartSite(): Site {
        return Site.EROMANGA
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        for (s in JS_URL_PATTERN_WHITELIST) client.adBlocker.addJsUrlPatternWhitelist(s)
        client.addJsContentBlacklist(*JS_CONTENT_BLACKLIST)

        return client
    }
}