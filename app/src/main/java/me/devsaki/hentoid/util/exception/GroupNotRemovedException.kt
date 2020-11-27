package me.devsaki.hentoid.util.exception

import me.devsaki.hentoid.database.domains.Group

open class GroupNotRemovedException : Exception {

    val group: Group

    constructor(group: Group, message: String, cause: Throwable) : super(message, cause) {
        this.group = group
    }

    constructor(group: Group, message: String) : super(message) {
        this.group = group
    }
}
