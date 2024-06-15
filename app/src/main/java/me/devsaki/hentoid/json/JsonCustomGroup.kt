package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.Grouping

@JsonClass(generateAdapter = true)
data class JsonCustomGroup(
    val name: String,
    val order: Int,
    val subtype: Int,
    val favourite: Boolean,
    val rating: Int,
    val hasCustomBookOrder: Boolean,
    val searchUri: String
) {
    constructor(g: Group) : this(
        g.name,
        g.order,
        g.subtype,
        g.isFavourite,
        g.rating,
        g.hasCustomBookOrder,
        g.searchUri
    )

    fun toEntity(grouping: Grouping): Group {
        return Group(grouping, name, order)
            .setSubtype(subtype)
            .setFavourite(favourite)
            .setRating(rating)
            .setHasCustomBookOrder(hasCustomBookOrder)
            .setSearchUri(searchUri)
    }
}