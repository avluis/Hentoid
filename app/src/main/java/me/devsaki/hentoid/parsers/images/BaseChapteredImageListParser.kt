package me.devsaki.hentoid.parsers.images

import android.webkit.URLUtil
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getChaptersFromLinks
import me.devsaki.hentoid.parsers.getExtraChaptersbyUrl
import me.devsaki.hentoid.parsers.getMaxChapterOrder
import me.devsaki.hentoid.parsers.getMaxImageOrder
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber

abstract class BaseChapteredImageListParser : BaseImageListParser() {

    protected abstract fun isChapterUrl(url: String): Boolean

    protected abstract fun getChapterSelector(): ChapterSelector

    protected abstract fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>? = null,
        fireProgressEvents: Boolean = true
    ): List<ImageFile>

    override fun parseImageList(
        content: Content,
        url: String
    ): List<ImageFile> {
        return if (isChapterUrl(url)) parseChapterImageListImpl(url, content)
        else parseImageListImpl(content, null)
    }

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
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
    open fun parseImageFiles(onlineContent: Content, storedContent: Content?): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        val headers = fetchHeaders(onlineContent)

        // 1. Scan the gallery page for chapter URLs
        val chapters: List<Chapter>
        val doc = getOnlineDocument(
            onlineContent.galleryUrl,
            headers,
            onlineContent.site.useHentoidAgent(),
            onlineContent.site.useWebviewAgent()
        ) ?: return result

        val selector = getChapterSelector()
        chapters = getChaptersFromLinks(
            getChapterLinks(doc, onlineContent, selector),
            onlineContent.id,
            selector.dateCssQuery,
            selector.datePattern
        )

        // If the stored content has chapters already, save them for comparison
        val storedChapters = storedContent?.chaptersList?.toMutableList() ?: emptyList()

        // Use chapter folder as a differentiator (as the whole URL may evolve)
        val extraChapters = getExtraChaptersbyUrl(storedChapters, chapters, this::getLastPartIndex)
        progressStart(onlineContent, storedContent, extraChapters.size)

        // Start numbering extra images right after the last position of stored and chaptered images
        val imgOffset = getMaxImageOrder(storedChapters)

        // 2. Open each chapter URL and get the image data until all images are found
        var minEpoch = Long.MAX_VALUE
        var storedOrderOffset = getMaxChapterOrder(storedChapters)
        extraChapters.forEach { chp ->
            if (processHalted.get()) return@forEach
            chp.order = ++storedOrderOffset
            if (chp.uploadDate > 0) minEpoch = minEpoch.coerceAtMost(chp.uploadDate)
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

        if (minEpoch > 0 && minEpoch < Long.MAX_VALUE) onlineContent.uploadDate = minEpoch

        // Add cover if it's a first download
        if (storedChapters.isEmpty()) result.add(
            ImageFile.newCover(onlineContent.coverImageUrl, StatusContent.SAVED)
        )

        return result
    }

    @Throws(Exception::class)
    fun parseChapterImageListImpl(url: String, content: Content): List<ImageFile> {
        require(URLUtil.isValidUrl(url)) { "Invalid gallery URL : $url" }
        if (processedUrl.isEmpty()) processedUrl = url
        Timber.d("Chapter URL: %s", url)
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            val ch = Chapter(url = url) // Forge a chapter
            result = parseChapterImageFiles(content, ch, 1)
            if (result.isNotEmpty() && content.coverImageUrl.isEmpty())
                content.coverImageUrl = result[0].url
            setDownloadParams(result, content.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    // == UTILS

    @Throws(EmptyResultException::class)
    protected open fun getChapterLinks(
        doc: Document,
        onlineContent: Content,
        selector: ChapterSelector
    ): List<Element> {
        val selectors =
            selector.selectors.map {
                it.replace("\$galleryUrl", onlineContent.galleryUrl)
            }
        val result = parseChapterLinks(doc, selectors)
        if (result.isEmpty()) throw EmptyResultException("No chapters found")
        return result
    }

    protected open fun getLastPartIndex(url: String): Int {
        return 0
    }

    private fun parseChapterLinks(doc: Document, selectors: List<String>): List<Element> {
        selectors.forEach {
            val links = doc.select(it).filterNotNull()
            if (links.isNotEmpty()) return links
        }
        return emptyList()
    }

    // == SELECTION INFO

    data class ChapterSelector(
        val selectors: List<String>,
        val dateCssQuery: String? = null,
        val datePattern: String? = null
    )
}