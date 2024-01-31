package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.util.LogHelper
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.HttpHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class ExHentaiParser : ImageListParser {

    private val progress = ParseProgress()

    override fun parseImageList(
        content: Content,
        url: String,
        log: LogHelper.LogInfo
    ): List<ImageFile> {
        return parseImageList(content, log)
    }

    override fun parseImageList(onlineContent: Content, storedContent: Content): List<ImageFile> {
        return parseImageList(onlineContent, null)
    }

    override fun parseImagePage(
        url: String,
        requestHeaders: List<Pair<String, String>>
    ): Pair<String, String?> {
        return EHentaiParser.parseImagePage(url, requestHeaders, Site.EXHENTAI)
    }

    override fun parseBackupUrl(
        url: String,
        requestHeaders: Map<String, String>,
        order: Int,
        maxPages: Int,
        chapter: Chapter?
    ): ImageFile? {
        return EHentaiParser.parseBackupUrl(
            url,
            Site.EXHENTAI,
            requestHeaders,
            order,
            maxPages,
            chapter
        )
    }

    override fun getAltUrl(url: String): String {
        return ""
    }

    override fun clear() {
        // No need for that here
    }

    @Throws(Exception::class)
    private fun parseImageList(content: Content, log : LogHelper.LogInfo?): List<ImageFile> {
        EventBus.getDefault().register(this)
        var result = emptyList<ImageFile>()
        try {
            // Retrieve and set cookies (optional; e-hentai can work without cookies even though certain galleries are unreachable)
            val cookieStr: String = EHentaiParser.getCookieStr(content)
            val headers: MutableList<Pair<String, String>> = ArrayList()
            headers.add(Pair(HttpHelper.HEADER_COOKIE_KEY, cookieStr))
            headers.add(Pair(HttpHelper.HEADER_REFERER_KEY, content.site.url))
            headers.add(Pair(HttpHelper.HEADER_ACCEPT_KEY, "*/*"))

            /*
             * A/ Without multipage viewer
             *    A.1- Detect the number of pages of the gallery
             *
             *    A.2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
             *
             *    A.3- Open all pages and grab the URL of the displayed image
             *
             * B/ With multipage viewer
             *    B.1- Open the MPV and parse gallery metadata
             *
             *    B.2- Call the API to get the pictures URL
             */
            val useHentoidAgent = Site.EXHENTAI.useHentoidAgent()
            val useWebviewAgent = Site.EXHENTAI.useWebviewAgent()
            val galleryDoc = HttpHelper.getOnlineDocument(
                content.galleryUrl,
                headers,
                useHentoidAgent,
                useWebviewAgent
            ) ?: throw ParseException("Unreachable gallery page")

            // Detect if multipage viewer is on
            val elements = galleryDoc.select(EHentaiParser.MPV_LINK_CSS)
            result = if (!elements.isEmpty()) {
                val mpvUrl = elements[0].attr("href")
                try {
                    EHentaiParser.loadMpv(
                        mpvUrl,
                        headers,
                        useHentoidAgent,
                        useWebviewAgent,
                        progress
                    )
                } catch (e: EmptyResultException) {
                    EHentaiParser.loadClassic(
                        content,
                        galleryDoc,
                        headers,
                        useHentoidAgent,
                        useWebviewAgent,
                        progress
                    )
                }
            } else {
                EHentaiParser.loadClassic(
                    content,
                    galleryDoc,
                    headers,
                    useHentoidAgent,
                    useWebviewAgent,
                    progress
                )
            }
            progress.complete()

            // If the process has been halted manually, the result is incomplete and should not be returned as is
            if (progress.isProcessHalted()) throw PreparationInterruptedException()
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    fun onDownloadCommand(event: DownloadCommandEvent) {
        when (event.type) {
            DownloadCommandEvent.Type.EV_PAUSE, DownloadCommandEvent.Type.EV_CANCEL, DownloadCommandEvent.Type.EV_SKIP -> progress.haltProcess()
            DownloadCommandEvent.Type.EV_UNPAUSE, DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT -> {}
        }
    }
}