package me.devsaki.hentoid.json.sources.luscious

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup

private const val RELATIVE_URL_PREFIX = "https://luscious.net"

@JsonClass(generateAdapter = true)
data class LusciousBookMetadata(
    val data: BookData
) {
    @JsonClass(generateAdapter = true)
    data class BookData(
        val album: BookInfoContainer
    )

    @JsonClass(generateAdapter = true)
    data class BookInfoContainer(
        val get: AlbumInfo?
    )

    @JsonClass(generateAdapter = true)
    data class AlbumInfo(
        val id: String?,
        val title: String?,
        val url: String?,
        val created: String?,
        @Json(name = "number_of_pictures")
        val nbPictures: Int?,
        val cover: CoverInfo?,
        val language: LanguageInfo?,
        val tags: List<TagInfo>?,
        @Json(name = "is_manga")
        val isManga: Boolean?
    )

    @JsonClass(generateAdapter = true)
    data class CoverInfo(
        val url: String
    )

    @JsonClass(generateAdapter = true)
    data class LanguageInfo(
        val title: String,
        val url: String
    )

    @JsonClass(generateAdapter = true)
    data class TagInfo(
        val text: String,
        val url: String
    )


    fun update(content: Content, updateImages: Boolean): Content {
        content.site = Site.LUSCIOUS

        val info = data.album.get
        if (info?.url == null || null == info.title) {
            content.status = StatusContent.IGNORED
            return content
        }

        content.url = info.url
        info.created?.let { if (it.isNotEmpty()) content.uploadDate = it.toLong() * 1000 }
        content.title = cleanup(info.title)

//        result.setQtyPages(info.number_of_pictures);  <-- does not reflect the actual number of pictures reachable via the Luscious API / website
        info.cover?.apply { content.coverImageUrl = url }

        val attributes = AttributeMap()
        info.language?.let {
            val name =
                cleanup(it.title.replace(" Language", ""))
            val attribute = Attribute(
                AttributeType.LANGUAGE,
                name,
                RELATIVE_URL_PREFIX + it.url,
                Site.LUSCIOUS
            )
            attributes.add(attribute)
        }
        info.tags?.forEach { tag ->
            var name = cleanup(tag.text)
            // Clean all tags starting with "Type :" (e.g. "Artist : someguy")
            if (name.contains(":")) name = name.substring(name.indexOf(':') + 1).trim()
            var type = AttributeType.TAG
            if (tag.url.startsWith("/tags/artist:")) type = AttributeType.ARTIST
            else if (tag.url.startsWith("/tags/parody:")) type = AttributeType.SERIE
            else if (tag.url.startsWith("/tags/character:")) type = AttributeType.CHARACTER
            else if (tag.url.startsWith("/tags/series:")) type = AttributeType.SERIE
            else if (tag.url.startsWith("/tags/group:")) type = AttributeType.ARTIST
            val attribute = Attribute(type, name, RELATIVE_URL_PREFIX + tag.url, Site.LUSCIOUS)
            attributes.add(attribute)
        }
        val attribute = Attribute(
            AttributeType.CATEGORY,
            if (info.isManga == true) "manga" else "picture set",
            "",
            Site.LUSCIOUS
        )
        attributes.add(attribute)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}