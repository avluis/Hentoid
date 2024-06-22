package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.Grouping

@JsonClass(generateAdapter = true)
data class JsonCustomGroup(
    val name: String,
    val order: Int,
    val subtype: Int?,
    val favourite: Boolean?,
    val rating: Int?,
    val hasCustomBookOrder: Boolean?,
    val searchUri: String?
) {
    constructor(g: Group) : this(
        g.name,
        g.order,
        g.subtype,
        g.favourite,
        g.rating,
        g.hasCustomBookOrder,
        g.searchUri
    )

    fun toEntity(grouping: Grouping): Group {
        return Group(
            grouping = grouping,
            name = name,
            order = order,
            subtype = subtype ?: 0,
            favourite = favourite ?: false,
            rating = rating ?: 0,
            hasCustomBookOrder = hasCustomBookOrder ?: false,
            searchUri = searchUri ?: ""
        )
    }
}