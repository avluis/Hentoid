package me.devsaki.hentoid.json.sources.hitomi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.parseDatetimeToEpoch

@JsonClass(generateAdapter = true)
data class HitomiGalleryInfo(
    val parodys: List<HitomiParody>? = null,
    val tags: List<HitomiTag>? = null,
    val title: String? = null,
    val characters: List<HitomiCharacter>? = null,
    val groups: List<HitomiGroup>? = null,
    // Format : "YYYY-MM-DD HH:MM:ss-05", "YYYY-MM-DD HH:MM:ss.SSS-05" or "YYYY-MM-DD HH:MM:ss.SS-05" (-05 being the timezone of the server ?)
    val date: String? = null,
    val language: String? = null,
    @Json(name = "language_localname")
    val languageName: String? = null,
    @Json(name = "language_url")
    val languageUrl: String? = null,
    val artists: List<HitomiArtist>? = null,
    val type: String? = null
) {

    data class HitomiParody(
        val parody: String,
        val url: String
    )

    data class HitomiTag(
        val url: String,
        val tag: String? = null,
        val female: String? = null,
        val male: String? = null
    ) {
        val label: String
            get() {
                var result = tag ?: ""
                if (female != null && female == "1") result += " ♀"
                else if (male != null && male == "1") result += " ♂"
                return result
            }
    }

    data class HitomiCharacter(
        val url: String,
        val character: String
    )

    data class HitomiGroup(
        val url: String,
        val group: String
    )

    data class HitomiArtist(
        val url: String,
        val artist: String
    )

    private fun addAttribute(
        attributeType: AttributeType,
        name: String,
        url: String,
        map: AttributeMap
    ) {
        val attribute = Attribute(attributeType, name, url, Site.HITOMI)
        map.add(attribute)
    }

    fun updateContent(content: Content) {
        content.title = cleanup(title)

        var uploadDate = parseDatetimeToEpoch(date!!, "yyyy-MM-dd HH:mm:ssx", false)
        if (0L == uploadDate) uploadDate =
            parseDatetimeToEpoch(date, "yyyy-MM-dd HH:mm:ss.SSSx", false)
        if (0L == uploadDate) uploadDate =
            parseDatetimeToEpoch(date, "yyyy-MM-dd HH:mm:ss.SSx", true)
        content.uploadDate = uploadDate

        val attributes = AttributeMap()
        parodys?.forEach {
            addAttribute(AttributeType.SERIE, it.parody, it.url, attributes)
        }
        tags?.forEach {
            addAttribute(AttributeType.TAG, it.label, it.url, attributes)
        }
        characters?.forEach {
            addAttribute(AttributeType.CHARACTER, it.character, it.url, attributes)
        }
        groups?.forEach {
            addAttribute(AttributeType.CIRCLE, it.group, it.url, attributes)
        }
        artists?.forEach {
            addAttribute(AttributeType.ARTIST, it.artist, it.url, attributes)
        }
        language?.let {
            addAttribute(AttributeType.LANGUAGE, it, languageUrl ?: "", attributes)
        }
        type?.let {
            addAttribute(AttributeType.CATEGORY, it, "", attributes)
        }
        content.putAttributes(attributes)
    }
}