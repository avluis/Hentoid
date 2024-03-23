package me.devsaki.hentoid.parsers.images

import android.webkit.URLUtil
import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.download.getCanonicalUrl
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.HttpHelper
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Element
import timber.log.Timber

class ManhwaParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        var part = parts[parts.size - 1]
        if (part.isEmpty()) part = parts[parts.size - 2]
        return part.contains("chap")
    }

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
        return emptyList()
    }

    override fun parseImages(
        chapterUrl: String, downloadParams: String?, headers: List<Pair<String, String>>?
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
        require(URLUtil.isValidUrl(readerUrl)) { "Invalid gallery URL : $readerUrl" }
        processedUrl = onlineContent.galleryUrl
        Timber.d("Gallery URL: %s", readerUrl)
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            result = parseImageFiles(onlineContent, storedContent)
            ParseHelper.setDownloadParams(result, onlineContent.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        Timber.d("%s", result)
        return result
    }

    @Throws(Exception::class)
    private fun parseImageFiles(onlineContent: Content, storedContent: Content?): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        val headers = fetchHeaders(onlineContent)

        // 1- Detect chapters on gallery page
        var chapters: List<Chapter> = ArrayList()
        var reason = ""
        var doc = HttpHelper.getOnlineDocument(
            onlineContent.galleryUrl,
            headers,
            Site.MANHWA.useHentoidAgent(),
            Site.MANHWA.useWebviewAgent()
        )
        if (doc != null) {
            val canonicalUrl = getCanonicalUrl(doc)
            // Retrieve the chapters page chunk
            doc = HttpHelper.postOnlineDocument(
                canonicalUrl + "ajax/chapters/",
                headers,
                Site.MANHWA.useHentoidAgent(), Site.MANHWA.useWebviewAgent(),
                "",
                HttpHelper.POST_MIME_TYPE
            )
            if (doc != null) {
                val chapterLinks: List<Element> = doc.select("[class^=wp-manga-chapter] a")
                chapters = ParseHelper.getChaptersFromLinks(chapterLinks, onlineContent.id)
            } else {
                reason = "Chapters page couldn't be downloaded @ $canonicalUrl"
            }
        } else {
            reason = "Index page couldn't be downloaded @ " + onlineContent.galleryUrl
        }
        if (chapters.isEmpty()) throw EmptyResultException("Unable to detect chapters : $reason")

        // If the stored content has chapters already, save them for comparison
        var storedChapters: List<Chapter>? = null
        if (storedContent != null) {
            storedChapters = storedContent.chapters
            if (storedChapters != null) storedChapters =
                storedChapters.toMutableList() // Work on a copy
        }
        if (null == storedChapters) storedChapters = emptyList()

        // Use chapter folder as a differentiator (as the whole URL may evolve)
        val extraChapters = ParseHelper.getExtraChaptersbyUrl(storedChapters, chapters)
        progressStart(onlineContent, storedContent, extraChapters.size)

        // Start numbering extra images right after the last position of stored and chaptered images
        val imgOffset = ParseHelper.getMaxImageOrder(storedChapters)

        // 2- Open each chapter URL and get the image data until all images are found
        var storedOrderOffset = ParseHelper.getMaxChapterOrder(storedChapters)
        for (chp in extraChapters) {
            chp.setOrder(++storedOrderOffset)
            result.addAll(
                parseChapterImageFiles(
                    onlineContent,
                    chp,
                    imgOffset + result.size + 1,
                    headers
                )
            )
            if (processHalted.get()) break
            progressPlus()
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()

        // Add cover if it's a first download
        if (storedChapters.isEmpty()) result.add(
            ImageFile.newCover(
                onlineContent.coverImageUrl,
                StatusContent.SAVED
            )
        )
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
            ParseHelper.setDownloadParams(result, content.site.url)
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
        val doc = HttpHelper.getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            content.site.useHentoidAgent(),
            content.site.useWebviewAgent()
        )
        if (doc != null) {
            val images: List<Element> = doc.select(".reading-content img")
            val urls = images.map { e -> ParseHelper.getImgSrc(e) }
                .filterNot { e -> e.isEmpty() }
            if (urls.isNotEmpty()) return ParseHelper.urlsToImageFiles(
                urls,
                targetOrder,
                StatusContent.SAVED,
                1000,
                chp
            ) else Timber.w("Chapter parsing failed for %s : no pictures found", chp.url)
        } else {
            Timber.w("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}