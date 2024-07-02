package me.devsaki.hentoid.parsers.images

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.activities.sources.MGG_CHAPTER_PATTERN
import me.devsaki.hentoid.activities.sources.MangagoActivity
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlToImageFile
import me.devsaki.hentoid.util.download.getDownloadLocation
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.getOrCreateContentDownloadDir
import me.devsaki.hentoid.util.image.MIME_IMAGE_GENERIC
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.views.WysiwygBackgroundWebView
import org.jsoup.nodes.Document
import timber.log.Timber

const val PIC_SELECTOR = "#pic_container img"

class MangagoParser : BaseChapteredImageListParser() {
    private var webview: WysiwygBackgroundWebView? = null

    override fun clear() {
        CoroutineScope(Dispatchers.Main).launch {
            webview?.clear()
            webview = null
        }
    }

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

    override fun getLastPartIndex(url: String): Int {
        return if (url.endsWith("/pg-1/") || url.endsWith("/page-1/")) 1 else 0
    }


    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        val results: MutableList<ImageFile> = ArrayList()
        var done = false

        // Create the book's folder to receive screencapped images
        val locationResult = getDownloadLocation(getInstance(), content) ?: return emptyList()
        var dir = locationResult.first
        val location = locationResult.second
        if (null == dir) dir = getOrCreateContentDownloadDir(
            getInstance(), content,
            location, false
        )

        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Main) {
                webview = WysiwygBackgroundWebView(
                    getInstance(),
                    MangagoActivity.MangagoWebClient(),
                    true,
                    dir
                )
            }

            Timber.d("Loading 1st page")
            val pageUrls: MutableList<String> = ArrayList()
            webview?.loadUrlBlocking(chp.url, 0, processHalted, true)?.let { res ->
                Timber.d("Document loaded !")
                res.doc?.let { doc ->
                    val pageNav = doc.select("#dropdown-menu-page a")
                    // The prefix of the URL is not necessarily the Site's, as Mangago can link chapters from its sister websites (e.g. mangago.zone, youhim.me)
                    val domain = UriParts(chp.url).host
                    pageUrls.addAll(pageNav.map { fixUrl(it.attr("href"), domain) })
                    processPageResult(doc, targetOrder, chp, results)
                } ?: throw EmptyResultException("Unable to detect chapter pages")
            }

            if (fireProgressEvents) progressStart(content)
            Timber.d("Looping through pages")
            var currentIndex = results.size
            var nbTries = -1
            while (results.size < pageUrls.size) {
                if (processHalted.get()) break
                if (currentIndex == results.size) nbTries++
                // Return with an incomplete chapter if current page fails to be captured more than 3 times
                if (nbTries > 3) {
                    Timber.d("No progress on %s, aborting chapter", pageUrls[results.size])
                    break
                }

                webview?.loadUrlBlocking(
                    pageUrls[results.size],
                    targetOrder + results.size,
                    processHalted
                )?.let { res ->
                    if (res.isImage) processImgResult(
                        getInstance(),
                        res.fileUri!!,
                        pageUrls[results.size],
                        targetOrder + results.size,
                        chp,
                        results
                    )
                    else processPageResult(res.doc!!, targetOrder + results.size, chp, results)
                    currentIndex = results.size
                    nbTries = 0
                    progressPlus(results.size * 1f / pageUrls.size)
                }
                Timber.d("%d pages found / %d", results.size, pageUrls.size)
            }
            if (fireProgressEvents) progressComplete()
            done = true
        }

        // Block calling thread until done
        var remainingIterations = 5 * 60 * 2 // Timeout 5 mins
        while (!done && remainingIterations-- > 0 && !processHalted.get()) pause(500)
        Timber.v("%s with %d iterations remaining", done, remainingIterations)
        if (processHalted.get()) throw EmptyResultException("Unable to detect pages (empty result)")

        return results
    }

    private fun processPageResult(
        doc: Document,
        order: Int,
        chp: Chapter,
        results: MutableList<ImageFile>
    ) {
        // First picture only not to mess up image order (would create gaps after de-duplicating)
        val pic = doc.select(PIC_SELECTOR).firstOrNull() ?: return

        // Cuz domain names with an _ (see https://github.com/google/conscrypt/issues/821)
        val url = getImgSrc(pic).replace("https:", "http:")

        Timber.v("%d PAGE result %s from %s", order, url, doc.baseUri())

        results.add(
            urlToImageFile(
                url,
                order,
                10000,
                StatusContent.SAVED,
                chp
            )
        )
    }

    private fun processImgResult(
        context: Context,
        fileUri: Uri,
        pageUrl: String,
        order: Int,
        chp: Chapter,
        result: MutableList<ImageFile>
    ) {
        val img = ImageFile(
            dbOrder = order,
            dbPageUrl = pageUrl,
            status = StatusContent.DOWNLOADED,
            fileUri = fileUri.toString()
        )
        Timber.v("%d IMG result : %s", order, pageUrl)
        img.computeName(5)
        img.setChapter(chp)
        // Enrich physical properties
        getFileFromSingleUriString(context, fileUri.toString())?.let {
            img.size = it.length()
            img.mimeType = it.type ?: MIME_IMAGE_GENERIC
        }
        result.add(img)
    }
}