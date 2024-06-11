package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.network.getOnlineDocument

class PururinParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return !url.contains("/collection/")
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("div.row-gallery a[href*='/gallery/']"))
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        val result: MutableList<String> = ArrayList()
        val doc = getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            Site.PURURIN.useHentoidAgent(),
            Site.PURURIN.useWebviewAgent()
        )
        if (doc != null) {
            // Get all thumb URLs and convert them to page URLs
            val imgSrc = doc.select(".gallery-preview img")
                .filterNotNull()
                .map { getImgSrc(it) }
                .map { thumbToPage(it) }
            result.addAll(imgSrc)
        }

        return urlsToImageFiles(result, targetOrder, StatusContent.SAVED, 1000, chp)
    }

    private fun thumbToPage(thumbUrl: String): String {
        val parts = UriParts(thumbUrl, true)
        val name = parts.fileNameNoExt
        parts.fileNameNoExt = name.substring(0, name.length - 1) // Remove the trailing 't'
        return parts.toUri()
    }
}