package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class AllPornComicActivity : BaseWebActivity() {

    companion object {
        const val GALLERY_PATTERN = "allporncomic.com/porncomic/[%\\w\\-]+/$"

        private const val DOMAIN_FILTER = "allporncomic.com"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "[%\\w\\-]+/$"))
        private val JS_WHITELIST = arrayOf(
            "$DOMAIN_FILTER/cdn",
            "$DOMAIN_FILTER/wp"
        )
        private val AD_ELEMENTS = arrayOf("iframe", ".c-ads")
    }


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