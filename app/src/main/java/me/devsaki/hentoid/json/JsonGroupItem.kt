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
        gi.linkedGroup?.grouping?.id ?: 0,
        gi.linkedGroup?.name ?: "",
        gi.order
    )

    fun toEntity(content: Content, group: Group): GroupItem {
        return GroupItem(content, group, order)
    }
}