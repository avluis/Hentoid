package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import java.io.IOException

interface ImageListParser {
    /**
     * Parse image list from the given URL, enriching the given Content if relevant (see Content.updatedProperties)
     */
    @Throws(Exception::class)
    fun parseImageList(content: Content, url: String): List<ImageFile>

    /**
     * Parse image list from the given online Content, retrieving only images from chapters
     * that aren't already in the given stored Content
     */
    @Throws(Exception::class)
    fun parseImageList(onlineContent: Content, storedContent: Content): List<ImageFile>

    /**
     * Returns image URL and backup URL from the given page URL
     */
    @Throws(IOException::class, LimitReachedException::class, EmptyResultException::class)
    fun parseImagePage(
        url: String,
        requestHeaders: List<Pair<String, String>>
    ): Pair<String, String?>

    @Throws(Exception::class)
    fun parseBackupUrl(
        url: String,
        requestHeaders: Map<String, String>,
        order: Int,
        maxPages: Int,
        chapter: Chapter?
    ): ImageFile?

    fun getAltUrl(url: String): String

    fun clear()
}