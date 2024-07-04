package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class GroupItem(
    @Id
    var id: Long = 0,
    var order: Int = 0
) {
    lateinit var content: ToOne<Content>
    lateinit var group: ToOne<Group>

    constructor(content: Content, group: Group, order: Int) : this(0, order) {
        this.content.target = content
        this.group.target = group
    }

    constructor(contentId: Long, group: Group, order: Int) : this(0, order) {
        content.targetId = contentId
        this.group.target = group
    }


    fun getContent(): Content? {
        return if (content.isResolved) content.target else null
    }

    fun getGroup(): Group {
        return group.target
    }

    val contentId: Long
        get() = content.targetId

    val groupId: Long
        get() = group.targetId
}