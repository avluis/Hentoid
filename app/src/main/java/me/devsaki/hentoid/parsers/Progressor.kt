package me.devsaki.hentoid.parsers

import me.devsaki.hentoid.database.domains.Content

interface Progressor {
    fun progressStart(
        onlineContent: Content,
        storedContent: Content? = null,
        maxSteps: Int = 1
    )

    fun progressPlus(progress: Float)

    fun progressNext()

    fun progressComplete()
}