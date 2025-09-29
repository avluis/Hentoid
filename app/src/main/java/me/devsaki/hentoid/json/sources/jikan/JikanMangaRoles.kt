package me.devsaki.hentoid.json.sources.jikan

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site

@JsonClass(generateAdapter = true)
data class JikanMangaRoles(
    val data: List<JikanRole>
) {
    @JsonClass(generateAdapter = true)
    data class JikanRole(
        val role: String,
        @Json(name = "manga")
        val content: JikanContent
    )

    fun toAttributes(): List<Attribute> {
        // Put main roles first ("Main" comes before "Supporting" in alpha order)
        return data
            .sortedBy { it.role }
            .map {
                Attribute(AttributeType.SERIE, it.content.title, it.content.url, Site.MAL)
            }.toList()
    }
}