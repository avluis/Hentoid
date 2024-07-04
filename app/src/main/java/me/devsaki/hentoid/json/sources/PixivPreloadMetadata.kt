package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import java.util.Date

/**
 * Data structure for Pixiv's "illust details" desktop website header data
 */
@JsonClass(generateAdapter = true)
data class PixivPreloadMetadata(
    val illust: Map<String, IllustData>? = null,
    val user: Map<String, UserData>? = null
) {
    fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.PIXIV

        if (illust.isNullOrEmpty()) {
            content.status = StatusContent.IGNORED
            return content
        }
        val illustData = illust.values.toList()[0]

        content.url = url.replace(Site.PIXIV.url, "")
        content.title = cleanup(illustData.title)
        content.uniqueSiteId = illustData.illustId ?: ""

        content.qtyPages = illustData.pageCount ?: 0
        content.coverImageUrl = illustData.thumbUrl
        content.uploadDate = illustData.uploadDate?.time ?: 0

        // Determine the prefix the user is navigating with (i.e. with or without language path)
        var pixivPrefix = Site.PIXIV.url
        val urlParts = url.replace(Site.PIXIV.url, "").split("/")
        val secondPath = urlParts[0]
        if (secondPath != "user" && secondPath != "artworks") pixivPrefix += "$secondPath/"

        val attributes = AttributeMap()

        var attribute = Attribute(
            AttributeType.ARTIST,
            illustData.userName ?: "",
            pixivPrefix + "user/" + illustData.userId,
            Site.PIXIV
        )
        attributes.add(attribute)

        for ((first, second) in illustData.getTags()) {
            val name = cleanup(second)
            val type = AttributeType.TAG
            attribute = Attribute(type, name, pixivPrefix + "tags/" + first, Site.PIXIV)
            attributes.add(attribute)
        }
        content.putAttributes(attributes)

        if (updateImages) content.setImageFiles(emptyList())

        return content
    }
}

@JsonClass(generateAdapter = true)
data class IllustData(
    val illustId: String? = null,
    val title: String? = null,
    val uploadDate: Date? = null,
    val pageCount: Int? = null,
    val urls: Map<String, String>? = null,
    @Json(name = "tags")
    val theTags: TagsData? = null,
    val userId: String? = null,
    val userName: String? = null
) {
    fun getTags(): List<Pair<String, String>> {
        return theTags?.getTags() ?: emptyList()
    }

    val thumbUrl: String
        get() {
            if (null == urls) return ""
            var result = urls["thumb"]
            if (null == result) result = urls["small"]
            return result ?: ""
        }
}

@JsonClass(generateAdapter = true)
data class TagsData(
    @Json(name = "tags")
    val theTags: List<TagData>? = null
) {
    fun getTags(): List<Pair<String, String>> {
        return theTags?.map { it.getTag() } ?: emptyList()
    }
}

@JsonClass(generateAdapter = true)
data class TagData(
    @Json(name = "tag")
    val theTag: String,
    val romaji: String? = null,
    val translation: Map<String, String>? = null
) {
    fun getTag(): Pair<String, String> {
        var label = translation?.get("en")
        if (null == label) label = romaji
        if (null == label) label = theTag
        return Pair(theTag, label)
    }
}

@JsonClass(generateAdapter = true)
data class UserData(
    val userId: String? = null,
    val name: String? = null
)