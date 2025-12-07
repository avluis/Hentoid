package me.devsaki.hentoid.json.sources.kemono

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.activities.sources.KEMONO_DOMAIN_FILTER
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site

@JsonClass(generateAdapter = true)
data class KemonoArtist(
    val id: String,
    val name: String,
    val service: String,
    @Json(name = "post_count")
    val postCount: Int
) {
    fun toAttribute(): Attribute {
        return Attribute(
            AttributeType.ARTIST,
            name,
            "https://$KEMONO_DOMAIN_FILTER/$service/user/$id/profile",
            Site.KEMONO
        )
    }

    val iconUrl
        get() = "https://img.$KEMONO_DOMAIN_FILTER/icons/$service/$id"
}