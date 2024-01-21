package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class ManhwaActivity : BaseWebActivity() {
    companion object {
        const val GALLERY_PATTERN = "//manhwahentai.me/[%\\w\\-]+/[%\\w\\-]{3,}/$"

        private const val DOMAIN_FILTER = "manhwahentai.me"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "ch[%\\w]+-[0-9]+/$")
        private val REMOVABLE_ELEMENTS = arrayOf(".c-ads")
        private val BLOCKED_CONTENT = arrayOf(".cloudfront.net")
    }


    override fun getStartSite(): Site {
        return Site.MANHWA
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        return client
    }
}