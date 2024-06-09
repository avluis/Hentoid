package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element

class AllPornComicParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").filterNot { it.isEmpty() }.count() > 4
    }

    @Throws(Exception::class)
    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()
        processedUrl = content.galleryUrl
        val headers = fetchHeaders(content)

        // 1. Scan the gallery page for chapter URLs
        val chapterUrls: MutableList<String> = ArrayList()
        getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.ALLPORNCOMIC.useHentoidAgent(),
            Site.ALLPORNCOMIC.useWebviewAgent()
        )?.let { doc ->
            val chapters: List<Element> = doc.select("[class^=wp-manga-chapter] a")
            for (e in chapters) {
                val link = e.attr("href")
                if (!chapterUrls.contains(link)) chapterUrls.add(link) // Make sure we're not adding duplicates
            }
        }
        chapterUrls.reverse() // Put the chapters in the correct reading order
        progressStart(content)

        // 2. Open each chapter URL and get the image data until all images are found
        chapterUrls.forEachIndexed { index, url ->
            if (processHalted.get()) return@forEachIndexed
            result.addAll(parseImages(url, null, headers))
            progressPlus((index + 1f) / chapterUrls.size)
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()
        progressComplete()
        return result
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        val theHeaders = headers ?: fetchHeaders(chapterUrl, downloadParams)

        if (processedUrl.isEmpty()) processedUrl = chapterUrl
        getOnlineDocument(
            chapterUrl,
            theHeaders,
            Site.ALLPORNCOMIC.useHentoidAgent(),
            Site.ALLPORNCOMIC.useWebviewAgent()
        )?.let { doc ->
            val images: List<Element> = doc.select("[class^=page-break] img")
            return images.map { getImgSrc(it) }.filterNot { it.isEmpty() }
        }
        return emptyList()
    }
}