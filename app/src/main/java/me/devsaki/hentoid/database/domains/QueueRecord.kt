package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.database.reach

@Entity
data class QueueRecord(
    @Id
    var id: Long = 0,
    var rank: Int = 0,
    var frozen: Boolean = false
) {
    lateinit var content: ToOne<Content>

    constructor(contentId: Long, rank: Int) : this(0, rank, false) {
        content.targetId = contentId
    }

    val linkedContent: Content?
        get() = content.reach(this)
}