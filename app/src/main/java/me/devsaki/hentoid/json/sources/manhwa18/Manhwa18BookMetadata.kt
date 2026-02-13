package me.devsaki.hentoid.json.sources.manhwa18

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.parseDatetimeToEpoch

@JsonClass(generateAdapter = true)
data class Manhwa18BookMetadata(
    val props: Manhwa18Properties,
    val url: String
) {
    @JsonClass(generateAdapter = true)
    data class Manhwa18Properties(
        val manga: Manhwa18Manga,
        val chapters: List<Manhwa18Chapter>
    )

    @JsonClass(generateAdapter = true)
    data class Manhwa18Manga(
        val id: Int,
        val name: String,
        val slug: String,
        @Json(name = "cover_url")
        val coverUrl: String?,
        @Json(name = "created_at")
        val createdAt: String?,
        @Json(name = "updated_at")
        val updatedAt: String?,
        val artists: List<Manhwa18Attribute>,
        val characters: List<Manhwa18Attribute>
    )

    @JsonClass(generateAdapter = true)
    data class Manhwa18Attribute(
        val name: String,
        val slug: String
    )

    @JsonClass(generateAdapter = true)
    data class Manhwa18Chapter(
        val id: Int,
        val slug: String,
        val name: String,
        val order: Int
    )

    fun update(content: Content, updateImages: Boolean): Content {
        content.site = Site.MANHWA18
        content.url = fixUrl(url, Site.MANHWA18.url)

        props.manga.createdAt?.let {
            if (it.isNotEmpty()) { // e.g. 2024-09-11 05:46:00
                content.uploadDate = parseDatetimeToEpoch(it, "yyyy-MM-dd HH:mm:ss")
            }
        }
        content.title = cleanup(props.manga.name)

        props.manga.coverUrl?.let { content.coverImageUrl = it }
        val attributes = AttributeMap()

        props.manga.artists.forEach { a ->
            val attribute =
                Attribute(AttributeType.ARTIST, a.name, "/artist/${a.slug}", Site.MANHWA18)
            attributes.add(attribute)
        }

        props.manga.characters.forEach { a ->
            val attribute =
                Attribute(AttributeType.CHARACTER, a.name, "/character/${a.slug}", Site.MANHWA18)
            attributes.add(attribute)
        }

        content.putAttributes(attributes)

        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }

    fun getChapters(contentId: Long): List<Chapter> {
        return props.chapters.sortedBy { it.order }
            .map {
                val ch = Chapter(
                    order = it.order,
                    name = it.name,
                    url = Site.MANHWA18.url + "manga/${props.manga.slug}/${it.slug}"
                )
                ch.setContentId(contentId)
            }
    }
}