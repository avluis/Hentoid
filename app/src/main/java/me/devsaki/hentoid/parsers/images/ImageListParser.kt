package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.util.LogHelper
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import java.io.IOException

interface ImageListParser {
    @Throws(Exception::class)
    fun parseImageList(content: Content, url: String, log : LogHelper.LogInfo): List<ImageFile>

    @Throws(Exception::class)
    fun parseImageList(onlineContent: Content, storedContent: Content): List<ImageFile>

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