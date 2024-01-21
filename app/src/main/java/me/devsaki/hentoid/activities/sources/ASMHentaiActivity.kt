package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class ASMHentaiActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "asmhentai.com"
        private val GALLERY_FILTER = arrayOf("asmhentai.com/g/")
        private val REMOVABLE_ELEMENTS = arrayOf(".atop")
        private val blockedContent = arrayOf("f.js")
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)
    }

    override fun getStartSite(): Site {
        return Site.ASMHENTAI
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*blockedContent)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        return client
    }
}