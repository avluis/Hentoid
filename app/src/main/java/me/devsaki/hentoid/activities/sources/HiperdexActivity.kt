package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class HiperdexActivity : BaseWebActivity() {
    companion object {
        const val GALLERY_PATTERN = "//hiperdex.com/[manga|comic]+/[%\\w\\-]+[/]{0,1}$"

        private const val DOMAIN_FILTER = "hiperdex.com"
        private val GALLERY_FILTER =
            arrayOf(GALLERY_PATTERN, GALLERY_PATTERN.replace("$", "") + "ch[%\\w]+-[0-9]+[%\\w\\-]*/$")
        private val REMOVABLE_ELEMENTS = arrayOf(".c-ads")
        private val JS_CONTENT_BLACKLIST = arrayOf("'iframe'", "'plu_slider_frame'")
        private val BLOCKED_CONTENT = arrayOf(".cloudfront.net")
    }

    override fun getStartSite(): Site {
        return Site.HIPERDEX
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        return client
    }
}