package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class MusesActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "8muses.com"
        private val GALLERY_FILTER =
            arrayOf("//www.8muses.com/comics/album/", "//comics.8muses.com/comics/album/")
        // private static final String[] REMOVABLE_ELEMENTS = {".c-tile:not([href])"}; // <-- even when removing empty tiles, ads are generated and force-inserted by the ad JS (!)
        private val JS_CONTENT_BLACKLIST = arrayOf("exoloader", "popunder")
    }

    override fun getStartSite(): Site {
        return Site.MUSES
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        return client
    }
}