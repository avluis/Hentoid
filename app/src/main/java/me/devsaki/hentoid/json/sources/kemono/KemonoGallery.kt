package me.devsaki.hentoid.json.sources.kemono

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.activities.sources.KEMONO_DOMAIN_FILTER
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.parseDatetimeToEpoch

@JsonClass(generateAdapter = true)
data class KemonoGallery(
    val post: KemonoPost,
    val previews: List<KemonoAttachment>
) {
    fun update(content: Content, galleryUrl: String, updateImages: Boolean): Content {
        content.site = Site.KEMONO
        content.url = galleryUrl.replace("/api/v1/", "/")
            .replace("/posts-legacy", "/")
        content.title = cleanup(post.title)
        content.status = StatusContent.SAVED
        content.uploadDate = 0L
        post.published?.let {
            if (it.isNotEmpty())
                content.uploadDate = parseDatetimeToEpoch(
                    it,
                    "yyyy-MM-dd'T'HH:mm:ss"
                ) // e.g. 2024-11-24T17:51:20
        }

        val attributes = AttributeMap()
        post.tags?.forEach {
            attributes.add(
                Attribute(
                    AttributeType.TAG,
                    it,
                    "https://$KEMONO_DOMAIN_FILTER/posts?tag=$it",
                    Site.KEMONO
                )
            )
        }
        content.putAttributes(attributes)

        // Map thumb server to picture URL
        val serverMapping = previews.associateBy({ it.path }, { it.server })
        val imageUrls = post.getImageUrls(serverMapping)
        // Use file as cover
        post.file?.let {
            content.coverImageUrl =
                "https://img.$KEMONO_DOMAIN_FILTER/thumbnail/data/${it.path}"
        } ?: run {
            content.coverImageUrl = imageUrls[0]
        }
        if (updateImages) {
            content.qtyPages = imageUrls.size
            content.setImageFiles(
                urlsToImageFiles(
                    imageUrls,
                    content.coverImageUrl,
                    StatusContent.SAVED
                )
            )
        }

        return content
    }
}