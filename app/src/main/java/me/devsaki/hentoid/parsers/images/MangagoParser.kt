package me.devsaki.hentoid.parsers.images

import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.activities.sources.MGG_CHAPTER_PATTERN
import me.devsaki.hentoid.activities.sources.MangagoActivity
import me.devsaki.hentoid.activities.sources.WebResultConsumer
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
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
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument
import me.devsaki.hentoid.views.WysiwygBackgroundWebView
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MangagoParser : BaseImageListParser(), WebResultConsumer {
    private val resultCode = AtomicInteger(-1)
    private val resultContent = AtomicReference<Content>()
    private var webview: WysiwygBackgroundWebView? = null

    override fun isChapterUrl(url: String): Boolean {
        return MGG_CHAPTER_PATTERN.matcher(url).find()
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
        var result: List<ImageFile>
        try {
            result = parseImageFiles(onlineContent, storedContent)
            setDownloadParams(result, onlineContent.site.url)
        } catch (e: Exception) {
            Helper.logException(e)
            result = emptyList()
        } finally {
            EventBus.getDefault().unregister(this)
            clear()
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
        val chapterLinks = doc.select("#chapter_table a[href*='/read-manga/']")
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
        for (chp in extraChapters) {
            result.addAll(
                parseChapterImageFiles(
                    onlineContent,
                    chp,
                    imgOffset + result.size + 1,
                    false
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
            result = parseChapterImageFiles(content, ch, 1)
            setDownloadParams(result, content.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
            clear()
        }
        return result
    }

    @Throws(Exception::class)
    fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        fireProgressEvents: Boolean = true
    ): List<ImageFile> {
        val picSelector = "#pic_container img"
        val result: MutableList<String> = ArrayList()
        var done = false

        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Main) {
                Timber.d("Attaching wv BEGIN")
                webview = WysiwygBackgroundWebView(
                    getInstance(),
                    MangagoActivity.MangagoWebClient(this@MangagoParser)
                )
                Timber.d("Attaching wv END")
            }

            Timber.d("Loading 1st page")
            val pageUrls: MutableList<String> = ArrayList()
            webview?.loadUrlBlocking(chp.url, processHalted)?.let { doc ->
                Timber.d("Document loaded !")
                val pageNav = doc.select("#dropdown-menu-page a")
                pageUrls.addAll(pageNav.map { fixUrl(it.attr("href"), content.site.url) })

                val pics = doc.select(picSelector)
                result.addAll(pics.map { getImgSrc(it) })
            }
            if (fireProgressEvents) progressStart(content, null, pageUrls.size)
            Timber.d("Looping through pages")
            while (result.size < pageUrls.size) {
                if (processHalted.get()) break
                webview?.loadUrlBlocking(pageUrls[result.size], processHalted)?.let { doc ->
                    Timber.d("Document loaded !")
                    val pics = doc.select(picSelector)
                    result.addAll(pics.map { getImgSrc(it) }
                        // Cuz domain names with an _ (see https://github.com/google/conscrypt/issues/821)
                        .map { it.replace("https:", "http:") }
                    )
                    if (fireProgressEvents) progressPlus(pics.size)
                }
                Timber.d("%d pages found / %d", result.size, pageUrls.size)
            }
            if (fireProgressEvents) progressComplete()
            done = true
        }

        // Block calling thread until done
        var remainingIterations = 5 * 60 * 2 // Timeout 5 mins
        while (!done && remainingIterations-- > 0 && !processHalted.get()) Helper.pause(500)
        Timber.v("%s with %d iterations remaining", done, remainingIterations)
        if (processHalted.get()) throw EmptyResultException("Unable to detect pages (empty result)")

        return urlsToImageFiles(result, targetOrder, StatusContent.SAVED, 1000, chp)
    }

    override fun clear() {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            webview?.destroy()
            webview = null
        }
    }

    override fun onContentReady(result: Content, quickDownload: Boolean) {
        resultContent.set(result)
        resultCode.set(0)
    }

    override fun onNoResult() {
        resultCode.set(1)
    }

    override fun onResultFailed() {
        resultCode.set(2)
    }
}