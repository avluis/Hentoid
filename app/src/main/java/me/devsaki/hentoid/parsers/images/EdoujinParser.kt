package me.devsaki.hentoid.parsers.images

import android.webkit.URLUtil
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getChaptersFromLinks
import me.devsaki.hentoid.parsers.getExtraChaptersbyUrl
import me.devsaki.hentoid.parsers.getMaxImageOrder
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException

class EdoujinParser : BaseImageListParser() {

    data class EdoujinInfo(private val sources: List<EdoujinSource>?) {
        fun getImages(): List<String> {
            val result: MutableList<String> = ArrayList()
            if (sources != null) {
                for (s in sources) if (s.images != null) result.addAll(s.images)
            }
            return result
        }
    }

    data class EdoujinSource(
        val images: List<String>?
    )

    companion object {
        @Throws(IOException::class)
        fun getDataFromScripts(scripts: List<Element>?): EdoujinInfo? {
            if (scripts != null) {
                for (e in scripts) {
                    if (e.childNodeSize() > 0
                        && e.childNode(0).toString().contains("\"noimagehtml\"")
                    ) {
                        var jsonStr = e.childNode(0).toString()
                            .replace("\n", "").trim()
                            .replace("});", "}")
                        jsonStr = jsonStr.substring(jsonStr.indexOf('{'))
                        return jsonToObject(jsonStr, EdoujinInfo::class.java)
                    }
                }
            }
            return null
        }
    }


    override fun isChapterUrl(url: String): Boolean {
        var parts = url.split("/")
        parts = parts[parts.size - 1].split("-")
        return StringHelper.isNumeric(parts[parts.size - 1])
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
        val result: MutableList<ImageFile> = java.util.ArrayList()
        val headers = fetchHeaders(onlineContent)

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        val chapters: List<Chapter>
        val doc = getOnlineDocument(
            onlineContent.galleryUrl,
            headers,
            Site.EDOUJIN.useHentoidAgent(),
            Site.EDOUJIN.useWebviewAgent()
        )
            ?: return result
        val chapterLinks: List<Element> = doc.select("#chapterlist .eph-num a")
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
        val theHeaders = headers ?: fetchHeaders(content)
        getOnlineDocument(
            chp.url,
            theHeaders,
            content.site.useHentoidAgent(),
            content.site.useWebviewAgent()
        )?.let { doc ->
            val scripts: List<Element> = doc.select("script")
            getDataFromScripts(scripts)?.let { info ->
                val imageUrls = info.getImages()
                if (imageUrls.isNotEmpty()) return urlsToImageFiles(
                    imageUrls,
                    targetOrder,
                    StatusContent.SAVED,
                    1000,
                    chp
                )
            } ?: Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } ?: {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
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
}