package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.network.getOnlineDocument

class AllPornComicParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").filterNot { it.isEmpty() }.count() > 4
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("[class^=wp-manga-chapter] a"))
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        if (processedUrl.isEmpty()) processedUrl = chp.url
        getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(chp.url),
            content.site.useHentoidAgent,
            content.site.useWebviewAgent
        )?.let { doc ->
            progressPlus(1f)
            val images = doc.select("[class^=page-break] img")
                .map { getImgSrc(it) }.filterNot { it.isEmpty() }
            return urlsToImageFiles(images, targetOrder, StatusContent.SAVED, 1000, chp)
        }
        return emptyList()
    }
}