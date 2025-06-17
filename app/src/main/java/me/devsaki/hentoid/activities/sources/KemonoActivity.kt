package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class KemonoActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = "kemono.su"
        private val GALLERY_FILTER = arrayOf("kemono.su/[\\w_%\\-]+/user/[\\d\\-]+/post/[\\d\\-]+$")
        private val BLOCKED_CONTENT = arrayOf("popunder")
        private val REMOVABLE_ELEMENTS = arrayOf("section.advertisement")
    }

    override fun getStartSite(): Site {
        return Site.KEMONO
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }
}