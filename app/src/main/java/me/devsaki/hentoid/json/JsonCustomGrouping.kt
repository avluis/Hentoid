package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.Grouping

@JsonClass(generateAdapter = true)
data class JsonCustomGrouping(
    val groupingId: Int,
    val groups: List<JsonCustomGroup> = ArrayList()
) {
    constructor(grouping: Grouping, groups: List<Group>) : this(
        grouping.id,
        groups.map { JsonCustomGroup(it) }
    )
}