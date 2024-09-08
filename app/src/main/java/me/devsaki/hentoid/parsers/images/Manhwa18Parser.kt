package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.network.getOnlineDocument
import timber.log.Timber

class Manhwa18Parser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        return parts[parts.size - 1].contains("chap")
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(
            listOf("div ul a[href*=chap]", "div ul a[href*=ch-]"),
            "div.chapter-time",
            "dd/MM/yyyy"
        )
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            content.site.useHentoidAgent(),
            content.site.useWebviewAgent()
        )?.let { doc ->
            val images = doc.select("#chapter-content img")
            val imageUrls = images.mapNotNull { getImgSrc(it) }
            if (imageUrls.isNotEmpty()) {
                progressPlus(1f)
                return urlsToImageFiles(
                    imageUrls,
                    targetOrder,
                    StatusContent.SAVED,
                    1000,
                    chp
                )
            } else Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } ?: run {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}