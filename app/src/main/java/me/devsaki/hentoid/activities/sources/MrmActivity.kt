package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class MrmActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "myreadingmanga.info"
        private val GALLERY_FILTER = arrayOf("myreadingmanga.info/[%\\w\\-]+/$")
        private val REMOVABLE_ELEMENTS = arrayOf("center.imgtop", "a[rel^='nofollow noopener']")
    }

    override fun getStartSite(): Site {
        return Site.MRM
    }

    override fun createWebClient(): CustomWebViewClient {
        val client: CustomWebViewClient = MrmWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }

    private class MrmWebClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        override fun isGalleryPage(url: String): Boolean {
            if (url.endsWith("/upload/")) return false
            if (url.endsWith("/whats-that-book/")) return false
            if (url.endsWith("/video-movie/")) return false
            if (url.endsWith("/yaoi-manga/")) return false
            if (url.endsWith("/contact/")) return false
            if (url.endsWith("/about/")) return false
            if (url.endsWith("/terms-service/")) return false
            if (url.endsWith("/my-bookmark/")) return false
            if (url.endsWith("/privacy-policy/")) return false
            if (url.endsWith("/dmca-notice/")) return false
            return if (url.contains("?relatedposts")) false else super.isGalleryPage(url)
        }
    }
}