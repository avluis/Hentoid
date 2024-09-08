package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.download.getCanonicalUrl
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.POST_MIME_TYPE
import me.devsaki.hentoid.util.network.getOnlineDocument
import me.devsaki.hentoid.util.network.postOnlineDocument
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PorncomixParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").count() > 6
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("[class^=wp-manga-chapter] a"))
    }

    override fun getChapterLinks(
        doc: Document,
        onlineContent: Content,
        selector: ChapterSelector
    ): List<Element> {
        val canonicalUrl = getCanonicalUrl(doc)
        val headers = fetchHeaders(onlineContent)
        // Retrieve the chapters page chunk
        postOnlineDocument(
            canonicalUrl + "ajax/chapters/",
            headers,
            Site.PORNCOMIX.useHentoidAgent(), Site.PORNCOMIX.useWebviewAgent(),
            "",
            POST_MIME_TYPE
        )?.let { return it.select(selector.selectors[0]) }
        throw EmptyResultException("Chapters page couldn't be downloaded @ $canonicalUrl")
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        // Fetch the book gallery page
        val doc = getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            Site.PORNCOMIX.useHentoidAgent(),
            Site.PORNCOMIX.useWebviewAgent()
        ) ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        var result = parseComixImages(content, doc, fireProgressEvents)
        if (result.isEmpty()) result = parseXxxToonImages(doc)
        if (result.isEmpty()) result = parseGedeComixImages(doc)
        if (result.isEmpty()) result = parseAllPornComixImages(doc)

        return urlsToImageFiles(result, targetOrder, StatusContent.SAVED, 1000, chp)
    }

    @Throws(Exception::class)
    fun parseComixImages(
        content: Content,
        doc: Document,
        fireProgressEvents: Boolean
    ): List<String> {
        val pagesNavigator: List<Element> = doc.select(".select-pagination select option")
        if (pagesNavigator.isEmpty()) return emptyList()
        val pageUrls = pagesNavigator.mapNotNull { it.attr("data-redirect") }.distinct()
        val result: MutableList<String> = ArrayList()
        if (fireProgressEvents) progressStart(content)
        pageUrls.forEachIndexed { index, pageUrl ->
            if (processHalted.get()) return@forEachIndexed
            getOnlineDocument(
                pageUrl,
                null,
                Site.PORNCOMIX.useHentoidAgent(),
                Site.PORNCOMIX.useWebviewAgent()
            )?.let {
                it.selectFirst(".entry-content img")?.let { img ->
                    result.add(getImgSrc(img))
                }
            }
            progressPlus((index + 1f) / pageUrls.size)
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()
        if (fireProgressEvents) progressComplete()
        return result
    }

    private fun parseXxxToonImages(doc: Document): List<String> {
        val pages: List<Element> = doc.select("figure.msnry_items a").filterNotNull()
        return if (pages.isEmpty()) emptyList()
        else pages.mapNotNull { it.attr("href") }.distinct()
    }

    private fun parseGedeComixImages(doc: Document): List<String> {
        val pages: List<Element> = doc.select(".reading-content img").filterNotNull()
        return if (pages.isEmpty()) emptyList()
        else pages.map { getImgSrc(it) }.distinct()
    }

    private fun parseAllPornComixImages(doc: Document): List<String> {
        val pages: List<Element> = doc.select("#jig1 a").filterNotNull()
        return if (pages.isEmpty()) emptyList()
        else pages.mapNotNull { it.attr("href") }.distinct()
    }
}