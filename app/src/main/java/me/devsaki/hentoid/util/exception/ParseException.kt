package me.devsaki.hentoid.util.exception

class ParseException : Exception {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
