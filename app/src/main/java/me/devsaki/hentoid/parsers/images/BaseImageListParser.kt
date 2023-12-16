package me.devsaki.hentoid.parsers.images

import android.webkit.URLUtil
import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.network.HttpHelper
import org.apache.commons.lang3.NotImplementedException
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseImageListParser : ImageListParser {
    private val progress = ParseProgress()
    protected var processHalted = AtomicBoolean(false)
    protected var processedUrl = ""

    protected abstract fun isChapterUrl(url: String): Boolean

    @Throws(Exception::class)
    protected abstract fun parseImages(content: Content): List<String>

    @Throws(Exception::class)
    protected abstract fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String>

    override fun parseImageList(content: Content, url: String): List<ImageFile> {
        return if (isChapterUrl(url)) parseChapterImageListImpl(url, content)
        else parseImageListImpl(content, null)
    }

    override fun parseImageList(onlineContent: Content, storedContent: Content): List<ImageFile> {
        return parseImageListImpl(onlineContent, storedContent)
    }

    override fun parseImagePage(
        url: String,
        requestHeaders: List<Pair<String, String>>
    ): Pair<String, String?> {
        throw NotImplementedException("Parser does not implement parseImagePage")
    }

    override fun parseBackupUrl(
        url: String,
        requestHeaders: Map<String, String>,
        order: Int,
        maxPages: Int,
        chapter: Chapter?
    ): ImageFile? {
        // Default behaviour; this class does not use backup URLs
        val img = ImageFile.fromImageUrl(order, url, StatusContent.SAVED, maxPages)
        if (chapter != null) img.setChapter(chapter)
        return img
    }

    override fun getAltUrl(url: String): String {
        return ""
    }

    override fun clear() {
        // No default implementation
    }

    /**
     * Default implementation for sources that don't enrich content over time by publishing extra chapters or pages
     */
    @Throws(Exception::class)
    protected open fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val readerUrl = onlineContent.readerUrl
        require(URLUtil.isValidUrl(readerUrl)) { "Invalid gallery URL : $readerUrl" }
        Timber.d("Gallery URL: %s", readerUrl)
        processedUrl = onlineContent.galleryUrl
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            val imgUrls = parseImages(onlineContent)
            result = ParseHelper.urlsToImageFiles(
                imgUrls,
                StatusContent.SAVED,
                onlineContent.coverImageUrl,
                null
            )
            ParseHelper.setDownloadParams(result, onlineContent.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    @Throws(Exception::class)
    protected open fun parseChapterImageListImpl(url: String, content: Content): List<ImageFile> {
        require(URLUtil.isValidUrl(url)) { "Invalid gallery URL : $url" }
        Timber.d("Chapter URL: %s", url)
        processedUrl = url
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            val imgUrls = parseImages(url, content.downloadParams, null)
            result = ParseHelper.urlsToImageFiles(imgUrls, StatusContent.SAVED, null, null)
            ParseHelper.setDownloadParams(result, content.site.url)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    open fun progressStart(onlineContent: Content, storedContent: Content?, maxSteps: Int) {
        if (progress.hasStarted()) return
        val storedId = storedContent?.id ?: -1
        progress.start(onlineContent.id, storedId, maxSteps)
    }

    open fun progressPlus() {
        progress.advance()
    }

    open fun progressComplete() {
        progress.complete()
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    open fun onDownloadCommand(event: DownloadCommandEvent) {
        when (event.type) {
            DownloadCommandEvent.Type.EV_PAUSE, DownloadCommandEvent.Type.EV_CANCEL, DownloadCommandEvent.Type.EV_SKIP
            -> processHalted.set(true)

            DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT ->
                if (event.content != null && event.content.galleryUrl == processedUrl) {
                    processHalted.set(true)
                    processedUrl = ""
                }

            DownloadCommandEvent.Type.EV_UNPAUSE -> {}
        }
    }

    protected open fun fetchHeaders(content: Content): List<Pair<String, String>> {
        return fetchHeaders(content.galleryUrl, content.downloadParams)
    }

    protected open fun fetchHeaders(
        url: String,
        downloadParams: String?
    ): List<Pair<String, String>> {
        val headers: MutableList<Pair<String, String>> = ArrayList()
        if (downloadParams != null) ParseHelper.addSavedCookiesToHeader(downloadParams, headers)
        headers.add(Pair(HttpHelper.HEADER_REFERER_KEY, url))
        return headers
    }
}