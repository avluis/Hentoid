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
import me.devsaki.hentoid.parsers.getMaxChapterOrder
import me.devsaki.hentoid.parsers.getMaxImageOrder
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Element
import timber.log.Timber

class Manhwa18Parser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        return parts[parts.size - 1].contains("chap")
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
        /// We won't use that as parseChapterImageListImpl is overriden directly
        return emptyList()
    }

    @Throws(Exception::class)
    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val readerUrl = onlineContent.readerUrl
        require(URLUtil.isValidUrl(readerUrl)) { "Invalid gallery URL : $readerUrl" }
        processedUrl = onlineContent.galleryUrl
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
            Site.MANHWA18.useHentoidAgent(),
            Site.MANHWA18.useWebviewAgent()
        )
            ?: return result
        var chapterLinks: List<Element> = doc.select("div ul a[href*=chap]").filterNotNull()
        if (chapterLinks.isEmpty()) chapterLinks = doc.select("div ul a[href*=ch-]").filterNotNull()
        chapters = getChaptersFromLinks(
            chapterLinks,
            onlineContent.id,
            "div.chapter-time",
            "dd/MM/yyyy"
        )

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
        var minEpoch = Long.MAX_VALUE
        var storedOrderOffset = getMaxChapterOrder(storedChapters)
        extraChapters.forEachIndexed { index, chp ->
            if (processHalted.get()) return@forEachIndexed
            chp.setOrder(++storedOrderOffset)
            if (chp.uploadDate > 0) minEpoch = minEpoch.coerceAtMost(chp.uploadDate)
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
        if (minEpoch > 0) {
            onlineContent.setUploadDate(minEpoch)
            onlineContent.isUpdatedProperties = true
        }
        progressComplete()

        // Add cover if it's a first download
        if (storedChapters.isEmpty()) result.add(
            ImageFile.newCover(
                onlineContent.coverImageUrl,
                StatusContent.SAVED
            )
        )
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
        getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            content.site.useHentoidAgent(),
            content.site.useWebviewAgent()
        )?.let { doc ->
            val images = doc.select("#chapter-content img")
            val imageUrls = images.mapNotNull { getImgSrc(it) }
            if (imageUrls.isNotEmpty()) return urlsToImageFiles(
                imageUrls,
                targetOrder,
                StatusContent.SAVED,
                1000,
                chp
            ) else Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } ?: {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}