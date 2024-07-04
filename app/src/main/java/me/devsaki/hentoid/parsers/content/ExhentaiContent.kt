package me.devsaki.hentoid.parsers.content

import android.webkit.CookieManager
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery
import me.devsaki.hentoid.retrofit.sources.EHentaiServer
import timber.log.Timber
import java.io.IOException

class ExhentaiContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val mgr = CookieManager.getInstance()
        val cookiesStr = mgr.getCookie(".exhentai.org")
        val galleryUrlParts = url.split("/")
        if (galleryUrlParts.size > 5) {
            val query = EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5])
            try {
                val metadata =
                    EHentaiServer.exentaiApi.getGalleryMetadata(query, cookiesStr).execute().body()
                if (metadata != null) return metadata.update(
                    content,
                    url,
                    Site.EXHENTAI,
                    updateImages
                )
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
        }
        return Content(site = Site.EXHENTAI, status = StatusContent.IGNORED)
    }
}