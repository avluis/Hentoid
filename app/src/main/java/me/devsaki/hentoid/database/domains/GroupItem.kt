package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.database.reach

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


    fun reachContent(): Content? {
        return content.reach(this)
    }

    fun reachGroup(): Group? {
        return group.reach(this)
    }

    val contentId: Long
        get() = content.targetId

    val groupId: Long
        get() = group.targetId
}