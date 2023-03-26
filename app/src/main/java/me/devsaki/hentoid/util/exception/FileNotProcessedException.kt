package me.devsaki.hentoid.util.exception

import me.devsaki.hentoid.database.domains.Content

class FileNotProcessedException : ContentNotProcessedException {

    constructor(content: Content, message: String) : super(content, message)
}
