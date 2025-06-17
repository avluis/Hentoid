package me.devsaki.hentoid.json.sources.kemono

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.parseDatetimeToEpoch

@JsonClass(generateAdapter = true)
data class KemonoGallery(
    val post: Post,
    val previews: List<Attachment>
) {
    @JsonClass(generateAdapter = true)
    data class Post(
        val id: String,
        val user: String,
        val service: String,
        val title: String,
        val published: String?,
        val tags: List<String>?,
        val file: Attachment?,
        val attachments: List<Attachment>
    )

    @JsonClass(generateAdapter = true)
    data class Attachment(
        val server: String?,
        val name: String?,
        val path: String
    )

    fun update(content: Content, galleryUrl: String, updateImages: Boolean): Content {
        content.site = Site.KEMONO
        content.url = galleryUrl.replace("/api/v1/", "/")
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
                    "https://kemono.su/posts?tag=$it",
                    Site.KEMONO
                )
            )
        }
        content.putAttributes(attributes)

        // Map thumb server to picture URL
        val thumbs = previews.associateBy({ it.path }, { it })
        val imageUrls = post.attachments
            .filter { isSupportedImage(it.path) }
            .map {
                val server = thumbs[it.path]?.server ?: ""
                "$server/data/${it.path}"
            }.distinct()
        if (imageUrls.isNotEmpty()) {
            post.file?.let {
                content.coverImageUrl = "https://img.kemono.su/thumbnail/data/${it.path}"
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
        }

        return content
    }
}