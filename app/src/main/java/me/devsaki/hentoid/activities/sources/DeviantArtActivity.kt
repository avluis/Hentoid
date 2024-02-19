package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class DeviantArtActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = ".deviantart.com"
        private val GALLERY_FILTER = arrayOf(
            "deviantart.com/[\\w\\-]+/gallery/",  // User gallery; multiple suffixes may be added
            "deviantart.com/[\\w\\-]+/art/[\\w\\-]+",  // Art page
            /*
            "pixiv.net/touch/ajax/illust/series_content/",  // Manga/series page (anthology) / load using fetch call
            "pixiv.net/touch/ajax/user/details\\?",  // User page / load using fetch call
            "pixiv.net/[\\w\\-]+/artworks/[0-9]+$",  // Illustrations page (single gallery)
            "pixiv.net/user/[0-9]+/series/[0-9]+$",  // Manga/series page (anthology)
            "pixiv.net/users/[0-9]+$" // User page
             */
        )
        private val JS_WHITELIST = arrayOf(DOMAIN_FILTER)

        private val NAVIGATION_QUERIES =
            arrayOf("/details?", "/search/illusts?", "ajax/pages/top?", "/tag_stories?")
    }


    override fun getStartSite(): Site {
        return Site.DEVIANTART
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = CustomWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(*JS_WHITELIST)
        return client
    }
}