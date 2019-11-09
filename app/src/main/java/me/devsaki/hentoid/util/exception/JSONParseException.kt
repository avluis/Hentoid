package me.devsaki.hentoid.util.exception

class JSONParseException : Exception {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
