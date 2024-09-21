package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem

@JsonClass(generateAdapter = true)
data class JsonGroupItem(
    val groupingId: Int,
    val groupName: String,
    val order: Int
) {
    constructor(gi: GroupItem) : this(
        gi.reachGroup()?.grouping?.id ?: 0,
        gi.reachGroup()?.name ?: "",
        gi.order
    )

    fun toEntity(content: Content, group: Group): GroupItem {
        return GroupItem(content, group, order)
    }
}