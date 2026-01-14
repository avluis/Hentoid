package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class TsuminoActivity : BaseBrowserActivity() {

    companion object {
        private const val DOMAIN_FILTER = "tsumino.com"
        private val GALLERY_FILTER = arrayOf("//www.tsumino.com/entry/")
    }

    override fun getStartSite(): Site {
        return Site.TSUMINO
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        return client
    }
}