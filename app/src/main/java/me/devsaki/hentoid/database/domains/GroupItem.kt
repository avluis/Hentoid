package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.database.safeReach

@Entity
data class GroupItem(
    @Id
    var id: Long = 0,
    var order: Int = 0,
    var size: Long = 0
) {
    lateinit var content: ToOne<Content>
    lateinit var group: ToOne<Group>

    constructor(content: Content, group: Group, order: Int, size: Long = 0) : this(0, order, size) {
        this.content.target = content
        this.group.target = group
    }

    constructor(contentId: Long, group: Group, order: Int, size: Long = 0) : this(0, order, size) {
        content.targetId = contentId
        this.group.target = group
    }

    val contentId: Long
        get() = content.targetId

    val groupId: Long
        get() = group.targetId

    val linkedContent: Content?
        get() = content.safeReach(this)

    val linkedGroup: Group?
        get() = group.safeReach(this)
}