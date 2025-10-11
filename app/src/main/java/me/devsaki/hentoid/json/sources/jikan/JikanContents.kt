package me.devsaki.hentoid.json.sources.jikan

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site

@JsonClass(generateAdapter = true)
data class JikanContents(
    val data: List<JikanContent>
) {
    fun toAttributes(): List<Attribute> {
        return data.map {
            Attribute(AttributeType.SERIE, it.title, it.url, Site.MAL)
        }.toList()
    }
}