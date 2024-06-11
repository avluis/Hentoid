package me.devsaki.hentoid.parsers.images

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
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.views.WysiwygBackgroundWebView
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

const val PIC_SELECTOR = "#pic_container img"

class MangagoParser : BaseChapteredImageListParser(), WebResultConsumer {
    private val resultCode = AtomicInteger(-1)
    private val resultContent = AtomicReference<Content>()
    private var webview: WysiwygBackgroundWebView? = null

    override fun isChapterUrl(url: String): Boolean {
        return MGG_CHAPTER_PATTERN.matcher(url).find()
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(
            listOf(
                "#chapter_table a[href*='/read-manga/']",
                "table.uk-table a[href*='/read-manga/']",
                "#chapter_table a[href*='/chapter/']",
                "table.uk-table a[href*='/chapter/']",
            )
        )
    }

    // Interesting part depends on where the chapter is hosted; assuming all chapters are stored in the same place for now....
    override fun getLastPartIndex(chapters: List<Chapter>): Int {
        return if (chapters.any { it.url.contains("mangago.me/") }) 1 else 0
    }


    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
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
                // The prefix of the URL is not necessarily the Site's, as Mangago can link chapters from its sister websites (e.g. mangago.zone, youhim.me)
                val domain = UriParts(chp.url).host
                pageUrls.addAll(pageNav.map { fixUrl(it.attr("href"), domain) })

                val pics = doc.select(PIC_SELECTOR)
                result.addAll(pics.map { getImgSrc(it) })
            }
            if (fireProgressEvents) progressStart(content)
            Timber.d("Looping through pages")
            while (result.size < pageUrls.size) {
                if (processHalted.get()) break
                webview?.loadUrlBlocking(pageUrls[result.size], processHalted)?.let { doc ->
                    Timber.d("Document loaded !")
                    val pics = doc.select(PIC_SELECTOR)
                    result.addAll(pics.map { getImgSrc(it) }
                        // Cuz domain names with an _ (see https://github.com/google/conscrypt/issues/821)
                        .map { it.replace("https:", "http:") }
                    )
                    progressPlus(result.size * 1f / pageUrls.size)
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
        CoroutineScope(Dispatchers.Main).launch {
            webview?.clear()
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