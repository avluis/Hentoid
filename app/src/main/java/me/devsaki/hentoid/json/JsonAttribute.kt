package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeLocation
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.cleanup
import java.util.Objects

@JsonClass(generateAdapter = true)
data class JsonAttribute(
    val name: String,
    val type: AttributeType,
    var url: String = ""
) {

    constructor(a: Attribute, site: Site) : this(cleanup(a.name), a.type) {
        this.computeUrl(a.locations, site)
    }

    private fun computeUrl(locations: List<AttributeLocation>, site: Site) {
        for (location in locations) {
            if (location.site == site) {
                url = location.url
                return
            }
        }
        url = "" // Field shouldn't be null
    }

    fun toEntity(site: Site): Attribute {
        return Attribute(type, cleanup(name), url, site)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as JsonAttribute
        return name == that.name && type == that.type && url == that.url
    }

    override fun hashCode(): Int {
        return Objects.hash(name, type, url)
    }
}