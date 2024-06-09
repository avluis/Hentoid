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
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class PururinParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return !url.contains("/collection/")
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
            Site.PURURIN.useHentoidAgent(),
            Site.PURURIN.useWebviewAgent()
        )
            ?: return result
        val chapterLinks = doc.select("div.row-gallery a[href*='/gallery/']")
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
        progressStart(onlineContent, storedContent)

        // Start numbering extra images right after the last position of stored and chaptered images
        val imgOffset = getMaxImageOrder(storedChapters)

        // 2. Open each chapter URL and get the image data until all images are found
        extraChapters.forEachIndexed { index, chp ->
            if (processHalted.get()) return@forEachIndexed
            result.addAll(
                parseChapterImageFiles(
                    onlineContent,
                    chp,
                    imgOffset + result.size + 1,
                    headers
                )
            )
            progressPlus(index + 1f / extraChapters.size)
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
        headers: List<Pair<String, String>>?
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
                .map { e -> getImgSrc(e) }
                .map { thumbUrl -> thumbToPage(thumbUrl) }
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