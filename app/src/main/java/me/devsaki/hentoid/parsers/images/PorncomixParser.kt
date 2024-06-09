package me.devsaki.hentoid.parsers.images

import android.webkit.URLUtil
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getChaptersFromLinks
import me.devsaki.hentoid.parsers.getExtraChaptersbyUrl
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.getMaxImageOrder
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber

class PorncomixParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return url.split("/").count() > 6
    }

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
        return emptyList()
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // We won't use that as parseChapterImageListImpl is overriden directly
        return emptyList()
    }

    @Throws(Exception::class)
    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val readerUrl = onlineContent.readerUrl
        processedUrl = onlineContent.galleryUrl
        require(URLUtil.isValidUrl(readerUrl)) { "Invalid gallery URL : $readerUrl" }
        Timber.d("Gallery URL: %s", readerUrl)
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            result = parseImageFiles(onlineContent, storedContent)
            setDownloadParams(result, onlineContent.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    @Throws(Exception::class)
    private fun parseImageFiles(onlineContent: Content, storedContent: Content?): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        val headers = fetchHeaders(onlineContent)

        // 1. Scan the gallery page for chapter URLs
        val chapters: List<Chapter>
        val doc = getOnlineDocument(
            onlineContent.galleryUrl,
            headers,
            Site.PORNCOMIX.useHentoidAgent(),
            Site.PORNCOMIX.useWebviewAgent()
        )
            ?: return result
        val chapterLinks: List<Element> =
            doc.select(".wp-manga-chapter a[href^=" + onlineContent.galleryUrl + "]")
        chapters = getChaptersFromLinks(chapterLinks, onlineContent.id)

        // If the stored content has chapters already, save them for comparison
        var storedChapters: List<Chapter>? = null
        if (storedContent != null) {
            storedChapters = storedContent.chapters
            if (storedChapters != null) storedChapters =
                storedChapters.toMutableList() // Work on a copy
        }
        if (null == storedChapters) storedChapters = emptyList()

        // Use chapter folder as a differentiator (as the whole URL may evolve)
        val extraChapters = getExtraChaptersbyUrl(storedChapters, chapters)
        progressStart(onlineContent, storedContent, extraChapters.size)

        // Start numbering extra images right after the last position of stored and chaptered images
        val imgOffset = getMaxImageOrder(storedChapters)

        // 2. Open each chapter URL and get the image data until all images are found
        extraChapters.forEach { chp ->
            if (processHalted.get()) return@forEach
            result.addAll(
                parseChapterImageFiles(
                    onlineContent,
                    chp,
                    imgOffset + result.size + 1,
                    headers,
                    false
                )
            )
            progressNext()
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()
        progressComplete()
        return result
    }

    @Throws(Exception::class)
    override fun parseChapterImageListImpl(url: String, content: Content): List<ImageFile> {
        require(URLUtil.isValidUrl(url)) { "Invalid gallery URL : $url" }
        if (processedUrl.isEmpty()) processedUrl = url
        Timber.d("Chapter URL: %s", url)
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            val ch = Chapter().setUrl(url) // Forge a chapter
            result = parseChapterImageFiles(content, ch, 1, null)
            setDownloadParams(result, content.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    @Throws(Exception::class)
    private fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean = true
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
        val pageUrls =
            pagesNavigator.mapNotNull { e -> e.attr("data-redirect") }.distinct()
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
            progressPlus(index + 1f / pageUrls.size)
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