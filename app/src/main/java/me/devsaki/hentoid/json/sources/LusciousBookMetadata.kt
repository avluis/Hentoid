package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.StringHelper

data class LusciousBookMetadata(
    val data: BookData
) {
    data class BookData(
        val album: BookInfoContainer
    )

    data class BookInfoContainer(
        val get: AlbumInfo?
    )

    data class AlbumInfo(
        val id: String,
        val title: String?,
        val url: String?,
        val created: String,
        @Json(name = "number_of_pictures")
        val nbPictures: Int,
        val cover: CoverInfo?,
        val language: LanguageInfo?,
        val tags: List<TagInfo>?,
        @Json(name = "is_manga")
        val isManga: Boolean
    )

    data class CoverInfo(
        val url: String
    )

    data class LanguageInfo(
        val title: String,
        val url: String
    )

    data class TagInfo(
        val text: String,
        val url: String
    )


    fun update(content: Content, updateImages: Boolean): Content {
        content.setSite(Site.LUSCIOUS)

        val info = data.album.get
        if (null == info || null == info.url || null == info.title)
            return content.setStatus(StatusContent.IGNORED)

        content.setUrl(info.url)
        if (info.created.isNotEmpty()) content.setUploadDate(info.created.toLong() * 1000)
        content.setTitle(StringHelper.removeNonPrintableChars(info.title))

//        result.setQtyPages(info.number_of_pictures);  <-- does not reflect the actual number of pictures reachable via the Luscious API / website
        info.cover?.apply { content.setCoverImageUrl(url) }

        val attributes = AttributeMap()
        info.language?.let {
            val name =
                StringHelper.removeNonPrintableChars(it.title.replace(" Language", ""))
            val attribute = Attribute(
                AttributeType.LANGUAGE,
                name,
                RELATIVE_URL_PREFIX + it.url,
                Site.LUSCIOUS
            )
            attributes.add(attribute)
        }
        info.tags?.forEach { tag ->
            var name = StringHelper.removeNonPrintableChars(tag.text)
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
            if (info.isManga) "manga" else "picture set",
            "",
            Site.LUSCIOUS
        )
        attributes.add(attribute)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        return content
    }

    companion object {
        private const val RELATIVE_URL_PREFIX = "https://luscious.net"
    }
}
