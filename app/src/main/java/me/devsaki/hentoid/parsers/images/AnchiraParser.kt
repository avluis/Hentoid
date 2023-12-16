package me.devsaki.hentoid.parsers.images

import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import androidx.core.util.Pair
import me.devsaki.hentoid.activities.sources.WebResultConsumer
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.HttpHelper.UriParts
import me.devsaki.hentoid.views.AnchiraBackgroundWebView
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AnchiraParser : BaseImageListParser(), WebResultConsumer {
    private val resultCode = AtomicInteger(-1)
    private val resultContent = AtomicReference<Content>()
    private var anchiraWv: AnchiraBackgroundWebView? = null

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
            result = parseImageListWithWebview(onlineContent)
            ParseHelper.setDownloadParams(result, onlineContent.site.url)
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
    fun parseContentWithWebview(url: String): Content {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            anchiraWv = AnchiraBackgroundWebView(
                getInstance(),
                this,
                Site.ANCHIRA,
                0
            )
            Timber.d(">> loading url %s", url)
            anchiraWv?.loadUrl(url)
            Timber.i(">> loading wv")
        }

        var remainingIterations = 30 // Timeout
        while (-1 == resultCode.get() && remainingIterations-- > 0 && !processHalted.get())
            Helper.pause(500)
        if (processHalted.get()) throw EmptyResultException("Unable to detect content (empty result)")

        synchronized(resultCode) {
            val res = resultCode.get()
            if (0 == res) {
                val c = resultContent.get()
                if (c != null) return c
            } else if (-1 == res) {
                throw ParseException("Parsing failed to start")
            } else if (2 == res) {
                throw ParseException("Parsing has failed unexpectedly")
            }
            throw EmptyResultException("Parsing hasn't found any content")
        }
    }

    @Throws(Exception::class)
    fun parseImageListWithWebview(onlineContent: Content): List<ImageFile> {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            anchiraWv = AnchiraBackgroundWebView(
                getInstance(),
                this,
                Site.ANCHIRA,
                1
            )
            val pageUrl = onlineContent.galleryUrl
            Timber.d(">> loading url %s", pageUrl)
            anchiraWv?.loadUrl(pageUrl)
            Timber.i(">> loading wv")
        }

        var remainingIterations = 30 // Timeout
        while (-1 == resultCode.get() && remainingIterations-- > 0 && !processHalted.get())
            Helper.pause(500)
        if (processHalted.get()) throw EmptyResultException("Unable to detect pages (empty result)")

        synchronized(resultCode) {
            val res = resultCode.get()
            if (0 == res) {
                val c = resultContent.get()
                // Might fail when called from a merged book where qtyPages accounts for all chapters
                val nbPages = onlineContent.qtyPages

                if (c != null) {
                    val imgUrl = c.coverImageUrl
                    Timber.d(">> retrieving url %s", imgUrl)
                    val parts = UriParts(imgUrl, false)
                    val fileName = parts.fileNameNoExt
                    val urls: MutableList<String> =
                        ArrayList()
                    if (StringHelper.isNumeric(fileName)) {
                        val length = fileName.length
                        for (i in 1..nbPages) {
                            parts.fileNameNoExt = StringHelper.formatIntAsStr(i, length)
                            parts.extension =
                                if (1 == i) "jpg" else "png" // Try to minimize failed requests
                            urls.add(parts.toUri())
                        }
                    } else {
                        val startIndex = fileName.lastIndexOf("-%20") + 4
                        val endIndex = fileName.lastIndexOf("%20(")
                        val length = endIndex - startIndex
                        val part1 = fileName.substring(0, startIndex)
                        val part2 = fileName.substring(endIndex)
                        for (i in 1..nbPages) {
                            parts.fileNameNoExt = part1 + StringHelper.formatIntAsStr(
                                i,
                                length
                            ) + part2
                            parts.extension =
                                if (1 == i) "jpg" else "png" // Try to minimize failed requests
                            urls.add(parts.toUri())
                        }
                    }
                    return ParseHelper.urlsToImageFiles(
                        urls,
                        onlineContent.coverImageUrl,
                        StatusContent.SAVED
                    )
                }
            } else if (-1 == res) {
                throw ParseException("Parsing failed to start")
            } else if (2 == res) {
                throw ParseException("Parsing has failed unexpectedly")
            }
            throw EmptyResultException("Parsing hasn't found any page")
        }
    }

    override fun clear() {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            anchiraWv?.destroy()
            anchiraWv = null
        }
    }

    override fun getAltUrl(url: String): String {
        val parts = UriParts(url, false)
        val ext = parts.extension
        val altExt = if (ext.equals("jpg", ignoreCase = true)) "png" else "jpg"
        parts.extension = altExt
        return parts.toUri()
    }

    override fun isChapterUrl(url: String): Boolean {
        // No chapters for this source
        return false
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
        // We won't use that as parseImageListImpl is overriden directly
        return emptyList()
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