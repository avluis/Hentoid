package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element

class MrmParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").filterNot { s -> s.isEmpty() }.count() > 3
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
                    if (e.hasClass("current")) chapterUrls.add(content.galleryUrl) // current chapter
                    else if (e.hasAttr("href")) chapterUrls.add(e.attr("href"))
                }
            }
        }
        if (chapterUrls.isEmpty()) chapterUrls.add(content.galleryUrl) // "one-shot" book

        progressStart(content, null, chapterUrls.size)

        // 2. Open each chapter URL and get the image data until all images are found
        for (url in chapterUrls) {
            result.addAll(parseImages(url, null, headers))
            if (processHalted.get()) break
            progressPlus()
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        if (result.isNotEmpty()) content.setCoverImageUrl(result[0])

        progressComplete()
        return result
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        if (processedUrl.isEmpty()) processedUrl = chapterUrl

        val doc = getOnlineDocument(
            chapterUrl,
            headers ?: fetchHeaders(chapterUrl, downloadParams),
            Site.MRM.useHentoidAgent(),
            Site.MRM.useWebviewAgent()
        )
        if (doc != null) {
            val images: List<Element> = doc.select(".entry-content img").filterNotNull()
            return images.map { e -> getImgSrc(e) }.filterNot { s -> s.isEmpty() }
        }
        return emptyList()
    }

}