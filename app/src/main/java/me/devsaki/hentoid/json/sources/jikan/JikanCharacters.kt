package me.devsaki.hentoid.json.sources.jikan

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site

@JsonClass(generateAdapter = true)
data class JikanCharacters(
    val data: List<JikanCharacter>
) {
    @JsonClass(generateAdapter = true)
    data class JikanCharacter(
        @Json(name = "mal_id")
        val id: String,
        val name: String,
        val url: String
    )

    fun toAttributes(): List<Attribute> {
        return data.map {
            Attribute(AttributeType.CHARACTER, it.name, it.url, Site.MAL)
        }.toList()
    }
}