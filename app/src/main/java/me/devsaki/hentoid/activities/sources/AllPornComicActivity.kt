package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

const val APC_GALLERY_PATTERN = "allporncomic.com/porncomic/[%\\w\\-]+/$"

private const val DOMAIN_FILTER = "allporncomic.com"
private val GALLERY_FILTER =
    arrayOf(APC_GALLERY_PATTERN, APC_GALLERY_PATTERN.replace("$", "[%\\w\\-]+/$"))
private val JS_WHITELIST = arrayOf(
    "$DOMAIN_FILTER/cdn",
    "$DOMAIN_FILTER/wp"
)
private val AD_ELEMENTS = arrayOf("iframe", ".c-ads")

class AllPornComicActivity : BaseWebActivity() {

    override fun getStartSite(): Site {
        return Site.ALLPORNCOMIC
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*AD_ELEMENTS)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        return client
    }
}