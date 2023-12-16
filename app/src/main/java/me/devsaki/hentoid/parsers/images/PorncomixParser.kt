package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PorncomixParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        processedUrl = content.galleryUrl

        // Fetch the book gallery page
        val doc = HttpHelper.getOnlineDocument(
            content.galleryUrl,
            null,
            Site.PORNCOMIX.useHentoidAgent(),
            Site.PORNCOMIX.useWebviewAgent()
        )
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        var result = parseComixImages(content, doc)
        if (result.isEmpty()) result = parseXxxToonImages(doc)
        if (result.isEmpty()) result = parseGedeComixImages(doc)
        if (result.isEmpty()) result = parseAllPornComixImages(doc)

        return result
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing; no chapters for this source
        return emptyList()
    }


    @Throws(Exception::class)
    fun parseComixImages(content: Content, doc: Document): List<String> {
        val pagesNavigator: List<Element> = doc.select(".select-pagination select option")
        if (pagesNavigator.isEmpty()) return emptyList()
        val pageUrls =
            pagesNavigator.mapNotNull { e -> e.attr("data-redirect") }.distinct()
        val result: MutableList<String> = ArrayList()
        progressStart(content, null, pageUrls.size)
        for (pageUrl in pageUrls) {
            val doc2 = HttpHelper.getOnlineDocument(
                pageUrl,
                null,
                Site.PORNCOMIX.useHentoidAgent(),
                Site.PORNCOMIX.useWebviewAgent()
            )
            if (doc2 != null) {
                val imageElement = doc.selectFirst(".entry-content img")
                if (imageElement != null) result.add(ParseHelper.getImgSrc(imageElement))
            }
            if (processHalted.get()) break
            progressPlus()
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()
        progressComplete()
        return result
    }

    private fun parseXxxToonImages(doc: Document): List<String> {
        val pages: List<Element> = doc.select("figure.msnry_items a").filterNotNull()
        return if (pages.isEmpty()) emptyList()
        else pages.mapNotNull { e -> e.attr("href") }.distinct()
    }

    private fun parseGedeComixImages(doc: Document): List<String> {
        val pages: List<Element> = doc.select(".reading-content img").filterNotNull()
        return if (pages.isEmpty()) emptyList()
        else pages.mapNotNull { e -> ParseHelper.getImgSrc(e) }.distinct()
    }

    private fun parseAllPornComixImages(doc: Document): List<String> {
        val pages: List<Element> = doc.select("#jig1 a").filterNotNull()
        return if (pages.isEmpty()) emptyList()
        else pages.mapNotNull { e -> e.attr("href") }.distinct()
    }
}