package me.devsaki.hentoid.json.sources.ehentai

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup

@JsonClass(generateAdapter = true)
data class EHentaiGalleriesMetadata(
    val gmetadata: List<EHentaiGalleryMetadata>? = null
) {
    fun update(content: Content, site: Site, updatePages: Boolean): Content {
        return if (gmetadata != null && !gmetadata.isEmpty()) gmetadata[0]
            .update(content, site, updatePages) else Content()
    }

    @JsonClass(generateAdapter = true)
    data class EHentaiGalleryMetadata(
        val gid: String? = null,
        val token: String? = null,
        val posted: String? = null,
        val title: String? = null,
        val category: String? = null,
        val thumb: String? = null,
        val filecount: String? = null,
        val tags: List<String>? = null
    ) {

        fun update(content: Content, site: Site, updatePages: Boolean): Content {
            val attributes = AttributeMap()

            content.site = site

            content.url =
                "/$gid/$token" // The rest will not be useful anyway because of temporary keys
            content.coverImageUrl = thumb ?: ""
            content.title = cleanup(title)
            content.status = StatusContent.SAVED

            if (category != null && !category.isBlank()) attributes.add(
                Attribute(
                    AttributeType.CATEGORY,
                    category.trim(),
                    "category/" + category.trim(),
                    site
                )
            )

            if (posted != null && !posted.isEmpty()) content.uploadDate = posted.toLong() * 1000

            if (updatePages) {
                if (filecount != null) content.qtyPages = filecount.toInt()
                else content.qtyPages = 0
                content.setImageFiles(mutableListOf<ImageFile>())
            }

            var tagParts: List<String>
            var type: AttributeType?
            var name: String

            tags?.forEach {
                tagParts = it.split(":")
                if (1 == tagParts.size) {
                    type = AttributeType.TAG
                    name = it
                } else {
                    name = tagParts[1]
                    when (tagParts[0]) {
                        "parody" -> type = AttributeType.SERIE
                        "character" -> type = AttributeType.CHARACTER
                        "language" -> type = AttributeType.LANGUAGE
                        "artist" -> type = AttributeType.ARTIST
                        "group" -> type = AttributeType.CIRCLE
                        else -> {
                            type = AttributeType.TAG
                            name = it
                        }
                    }
                }

                attributes.add(Attribute(type, name, type.name + "/" + name, site))
            }
            content.putAttributes(attributes)

            return content
        }
    }
}
