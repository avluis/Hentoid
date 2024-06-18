package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument

class MrmParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").filterNot { s -> s.isEmpty() }.count() > 3
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("div.entry-pagination"))
    }

    override fun parseImageFiles(onlineContent: Content, storedContent: Content?): List<ImageFile> {
        return urlsToImageFiles(
            parseImages(onlineContent),
            onlineContent.coverImageUrl,
            StatusContent.SAVED
        )
    }

    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()
        processedUrl = content.galleryUrl

        val headers = fetchHeaders(content)

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        val chapterUrls: MutableList<String> = ArrayList()
        val doc = getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.MRM.useHentoidAgent(),
            Site.MRM.useWebviewAgent()
        )
        if (doc != null) {
            val chapterContainer = doc.select("div.entry-pagination").first()
            if (chapterContainer != null) {
                for (e in chapterContainer.children()) {
                    if (e.hasClass("current")) chapterUrls.add(content.galleryUrl) // current chapter; this is the reason why MrmParser still has its own parseImages function
                    else if (e.hasAttr("href")) chapterUrls.add(e.attr("href"))
                }
            }
        }
        if (chapterUrls.isEmpty()) chapterUrls.add(content.galleryUrl) // "one-shot" book

        progressStart(content)

        // 2. Open each chapter URL and get the image data until all images are found
        chapterUrls.forEachIndexed { index, url ->
            if (processHalted.get()) return@forEachIndexed
            result.addAll(parseImages(url, headers))
            progressPlus((index + 1f) / chapterUrls.size)
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        if (result.isNotEmpty()) content.coverImageUrl = result[0]

        progressComplete()
        return result
    }

    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        return urlsToImageFiles(
            parseImages(chp.url, headers),
            targetOrder, StatusContent.SAVED, 1000, chp
        )
    }

    fun parseImages(
        chapterUrl: String,
        headers: List<Pair<String, String>>? = null
    ): List<String> {
        if (processedUrl.isEmpty()) processedUrl = chapterUrl

        getOnlineDocument(
            chapterUrl,
            headers ?: fetchHeaders(chapterUrl),
            Site.MRM.useHentoidAgent(),
            Site.MRM.useWebviewAgent()
        )?.let { doc ->
            val images = doc.select(".entry-content img").filterNotNull()
            return images.map { getImgSrc(it) }.filterNot { it.isEmpty() }
        }
        return emptyList()
    }

}