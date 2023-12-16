package me.devsaki.hentoid.parsers.images

import android.webkit.URLUtil
import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.HttpHelper
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException

class Hentai2ReadParser : BaseImageListParser() {
    companion object {
        const val IMAGE_PATH = "https://static.hentaicdn.com/hentai"

        @Throws(IOException::class)
        fun getDataFromScripts(scripts: List<Element>?): H2RInfo? {
            if (scripts != null) {
                for (e in scripts) {
                    if (e.childNodeSize() > 0 && e.childNode(0).toString().contains("'images' :")) {
                        val jsonStr = e.childNode(0).toString().replace("\n", "").trim { it <= ' ' }
                            .replace("var gData = ", "").replace("};", "}")
                        return JsonHelper.jsonToObject(jsonStr, H2RInfo::class.java)
                    }
                }
            }
            return null
        }
    }

    data class H2RInfo(
        val title: String,
        val images: List<String>
    )


    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        var part = parts[parts.size - 1]
        if (part.isEmpty()) part = parts[parts.size - 2]
        return StringHelper.isNumeric(part)
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
            ParseHelper.setDownloadParams(result, onlineContent.site.url)
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
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        val chapters: List<Chapter>
        val doc = HttpHelper.getOnlineDocument(
            onlineContent.galleryUrl,
            headers,
            Site.HENTAI2READ.useHentoidAgent(),
            Site.HENTAI2READ.useWebviewAgent()
        )
            ?: return result
        val chapterLinks: List<Element> =
            doc.select(".nav-chapters a[href^=" + onlineContent.galleryUrl + "]")
        chapters = ParseHelper.getChaptersFromLinks(chapterLinks, onlineContent.id)

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

        // 2. Open each chapter URL and get the image data until all images are found
        for (chp in extraChapters) {
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
            val scripts: List<Element> = doc.select("script")
            val info = getDataFromScripts(scripts)
            if (info != null) {
                val imageUrls = info.images.map { s -> IMAGE_PATH + s }
                if (imageUrls.isNotEmpty()) return ParseHelper.urlsToImageFiles(
                    imageUrls,
                    targetOrder,
                    StatusContent.SAVED,
                    1000,
                    chp
                )
            } else Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } else {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}