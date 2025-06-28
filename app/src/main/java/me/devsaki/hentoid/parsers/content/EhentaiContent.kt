package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.ehentai.EHentaiGalleryQuery
import me.devsaki.hentoid.retrofit.sources.EHentaiServer
import timber.log.Timber
import java.io.IOException

class EhentaiContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val galleryUrlParts = url.split("/")
        if (galleryUrlParts.size > 5) {
            val query = EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5])
            try {
                val metadata =
                    EHentaiServer.ehentaiApi.getGalleryMetadata(query, null).execute().body()
                if (metadata != null) return metadata.update(
                    content,
                    Site.EHENTAI,
                    updateImages
                )
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
        }
        return Content(site = Site.EXHENTAI, status = StatusContent.IGNORED)
    }
}