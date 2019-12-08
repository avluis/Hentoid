package me.devsaki.hentoid.util.exception

import me.devsaki.hentoid.database.domains.Content

class ContentNotRemovedException : Exception {

    val content: Content

    constructor(content: Content, message: String, cause: Throwable) : super(message, cause) {
        this.content = content
    }

    constructor(content: Content, message: String) : super(message) {
        this.content = content
    }
}
