package me.devsaki.hentoid.parsers.images

import android.webkit.CookieManager
import android.webkit.URLUtil
import com.squareup.moshi.Json
import com.squareup.moshi.Types
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.json.sources.EHentaiImageMetadata
import me.devsaki.hentoid.json.sources.EHentaiImageQuery
import me.devsaki.hentoid.json.sources.EHentaiImageResponse
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.getSavedCookieStr
import me.devsaki.hentoid.parsers.urlToImageFile
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.network.HEADER_ACCEPT_KEY
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument
import me.devsaki.hentoid.util.network.parseCookies
import me.devsaki.hentoid.util.network.postOnlineResource
import me.devsaki.hentoid.util.network.webkitRequestHeadersToOkHttpHeaders
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale

class EHentaiParser : ImageListParser {

    enum class EhAuthState {
        UNLOGGED,
        UNLOGGED_ABNORMAL,
        LOGGED
    }

    private val progress = ParseProgress()

    class MpvInfo {
        var gid = 0
        var mpvkey = ""
        var apiUrl = ""
        var images: List<EHentaiImageMetadata> = ArrayList()
        var pagecount = 0
        fun getImageInfo(index: Int): MpvImageInfo {
            return MpvImageInfo(
                gid,
                index + 1,
                mpvkey,
                apiUrl,
                images[index],
                pagecount
            )
        }
    }

    data class MpvImageInfo(
        val gid: Int,
        val pageNum: Int,
        val mpvkey: String,
        @Json(name = "api_url")
        val apiUrl: String,
        val image: EHentaiImageMetadata,
        val pagecount: Int
    )


    companion object {

        // TODO : Try thumbnails (#gdt div) to detect if MPV is actually enabled
        const val MPV_LINK_CSS = "#gmid a[href*='/mpv/']"

        private const val LIMIT_509_URL = "/509.gif"

        @Throws(IOException::class, LimitReachedException::class, EmptyResultException::class)
        fun parseImagePageMpv(
            json: String,
            requestHeaders: List<Pair<String, String>>,
            site: Site
        ): Pair<String, String?> {
            val mpvInfo = JsonHelper.jsonToObject(json, MpvImageInfo::class.java)
            val imageMetadata = getMpvImage(
                mpvInfo,
                requestHeaders,
                site.useHentoidAgent(),
                site.useWebviewAgent()
            )
            var imageUrl = imageMetadata.url
            // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
            if (imageUrl.contains(LIMIT_509_URL)) throw LimitReachedException("E(x)-hentai download points regenerate over time or can be bought on e(x)-hentai if you're in a hurry")
            if (Preferences.isDownloadEhHires()) {
                // Check if the user is logged in
                if (getAuthState(site.url) != EhAuthState.LOGGED)
                    throw EmptyResultException("You need to be logged in to download full-size images.")
                // Use full image URL, if available
                val fullImgUrl = imageMetadata.fullUrlRelative
                if (fullImgUrl.isNotEmpty()) imageUrl = fixUrl(fullImgUrl, site.url)
            }
            return Pair(imageUrl, null)
        }

        @Throws(IOException::class, LimitReachedException::class, EmptyResultException::class)
        fun parseImagePageClassic(
            url: String,
            requestHeaders: List<Pair<String, String>>,
            site: Site
        ): Pair<String, String?> {
            val doc = getOnlineDocument(
                url,
                requestHeaders,
                site.useHentoidAgent(),
                site.useWebviewAgent()
            )
            if (doc != null) {
                var imageUrl = getDisplayedImageUrl(doc).lowercase(Locale.getDefault())
                // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
                if (imageUrl.contains(LIMIT_509_URL)) throw LimitReachedException("E(x)-hentai download points regenerate over time or can be bought on e(x)-hentai if you're in a hurry")
                val backupUrl: String?
                if (Preferences.isDownloadEhHires()) {
                    // Check if the user is logged in
                    if (getAuthState(site.url) != EhAuthState.LOGGED)
                        throw EmptyResultException("You need to be logged in to download full-size images.")
                    // Use full image URL, if available
                    val fullUrl = getFullImageUrl(doc).lowercase(Locale.getDefault())
                    if (fullUrl.isNotEmpty()) imageUrl = fullUrl
                    backupUrl = null
                } else {
                    backupUrl = getBackupPageUrl(doc, url)
                }
                if (imageUrl.isNotEmpty()) return Pair(imageUrl, backupUrl)
            }
            throw EmptyResultException("Page contains no picture data : $url")
        }

        @Throws(IOException::class, LimitReachedException::class, EmptyResultException::class)
        fun parseImagePage(
            url: String,
            requestHeaders: List<Pair<String, String>>,
            site: Site
        ): Pair<String, String?> {
            return if (url.startsWith("http"))
                parseImagePageClassic(url, requestHeaders, site)
            else parseImagePageMpv(url, requestHeaders, site)
        }

        /**
         * Retrieve cookies (optional; e-hentai can work without cookies even though certain galleries are unreachable)
         *
         * @param content Content to retrieve cookies from
         * @return Cookie string
         */
        fun getCookieStr(content: Content): String {
            val cookieStr = getSavedCookieStr(content.downloadParams)
            return cookieStr.ifEmpty { "nw=1" }
        }

        fun getAuthState(url: String): EhAuthState {
            var domain = ""
            if (url.startsWith("https://exhentai.org")) domain = ".exhentai.org"
            else if (url.contains("e-hentai.org")) domain = ".e-hentai.org"
            if (domain.isEmpty()) return EhAuthState.UNLOGGED

            val cookiesStr = CookieManager.getInstance().getCookie(domain)
            if (cookiesStr != null) {
                if (cookiesStr.contains("igneous=mystery"))
                    return EhAuthState.UNLOGGED_ABNORMAL
                else if (cookiesStr.contains("ipb_member_id=")) {
                    // may contain ipb_member_id=0, e.g. after unlogging manually
                    val cookies = parseCookies(cookiesStr)
                    val memberId = cookies["ipb_member_id"]
                    return if (memberId != null && memberId != "0") EhAuthState.LOGGED else EhAuthState.UNLOGGED
                }
            }
            return EhAuthState.UNLOGGED
        }

        @Throws(Exception::class)
        fun parseBackupUrl(
            url: String,
            site: Site,
            requestHeaders: Map<String, String>,
            order: Int,
            maxPages: Int,
            chapter: Chapter?
        ): ImageFile? {
            val reqHeaders = webkitRequestHeadersToOkHttpHeaders(requestHeaders, url)
            val doc = getOnlineDocument(
                url,
                reqHeaders,
                site.useHentoidAgent(),
                site.useWebviewAgent()
            )
            if (doc != null) {
                val imageUrl = getDisplayedImageUrl(doc).lowercase(Locale.getDefault())
                // If we have the 509.gif picture, it means the bandwidth limit for e-h has been reached
                if (imageUrl.contains(LIMIT_509_URL)) throw LimitReachedException(site.description + " download points regenerate over time or can be bought if you're in a hurry")
                if (imageUrl.isNotEmpty()) return urlToImageFile(
                    imageUrl,
                    order,
                    maxPages,
                    StatusContent.SAVED,
                    chapter
                )

            }
            return null
        }

        @Throws(EmptyResultException::class, IOException::class)
        private fun getMpvImage(
            imageInfo: MpvImageInfo,
            headers: List<Pair<String, String>>,
            useHentoidAgent: Boolean,
            useWebviewAgent: Boolean
        ): EHentaiImageResponse {
            val query = EHentaiImageQuery(
                imageInfo.gid,
                imageInfo.image.key,
                imageInfo.mpvkey,
                imageInfo.pageNum
            )
            val jsonRequest = JsonHelper.serializeToJson(
                query,
                EHentaiImageQuery::class.java
            )
            var bodyStr: String
            postOnlineResource(
                imageInfo.apiUrl,
                headers,
                true,
                useHentoidAgent,
                useWebviewAgent,
                jsonRequest,
                JsonHelper.JSON_MIME_TYPE
            ).use { response ->
                val body = response.body
                    ?: throw EmptyResultException("API " + imageInfo.apiUrl + " returned an empty body")
                bodyStr = body.string()
            }
            if (!bodyStr.contains("{") || !bodyStr.contains("}")) throw EmptyResultException("API " + imageInfo.apiUrl + " returned non-JSON data")
            return JsonHelper.jsonToObject(bodyStr, EHentaiImageResponse::class.java)
        }

        @Throws(IOException::class, EmptyResultException::class)
        fun loadMpv(
            mpvUrl: String,
            headers: List<Pair<String, String>>,
            useHentoidAgent: Boolean,
            useWebviewAgent: Boolean,
            progress: ParseProgress
        ): List<ImageFile> {
            val result: MutableList<ImageFile> = ArrayList()

            // B.1- Open the MPV and parse gallery metadata
            val mpvInfo = parseMpvPage(mpvUrl, headers, useHentoidAgent, useWebviewAgent)
                ?: throw EmptyResultException("No exploitable data has been found on the multiple page viewer")
            val pageCount = mpvInfo.pagecount.coerceAtMost(mpvInfo.images.size)
            var pageNum = 1
            while (pageNum <= pageCount && !progress.isProcessHalted()) {

                // Get the URL of he 1st page as the cover
                if (1 == pageNum) {
                    val imageMetadata = getMpvImage(
                        mpvInfo.getImageInfo(0),
                        headers,
                        useHentoidAgent,
                        useWebviewAgent
                    )
                    result.add(ImageFile.newCover(imageMetadata.url, StatusContent.SAVED))
                }
                // Add page URLs to be read later by the downloader
                result.add(
                    ImageFile.fromPageUrl(
                        pageNum,
                        JsonHelper.serializeToJson(
                            mpvInfo.getImageInfo(pageNum - 1),
                            MpvImageInfo::class.java
                        ),
                        StatusContent.SAVED, pageCount
                    )
                )
                pageNum++
            }
            return result
        }

        @Throws(IOException::class)
        fun loadClassic(
            content: Content,
            galleryDoc: Document,
            headers: List<Pair<String, String>>,
            useHentoidAgent: Boolean,
            useWebviewAgent: Boolean,
            progress: ParseProgress
        ): List<ImageFile> {
            // A.1- Detect the number of pages of the gallery
            val elements = galleryDoc.select("table.ptt a")
            if (elements.isEmpty()) throw ParseException("No exploitable data has been found on the gallery")

            val tabId = if (1 == elements.size) 0 else elements.size - 2
            val nbGalleryPages = elements[tabId].text().toInt()
            progress.start(content.id, -1, nbGalleryPages)

            // 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
            val pageUrls: MutableList<String> = ArrayList()
            fetchPageUrls(galleryDoc, pageUrls)
            if (nbGalleryPages > 1) {
                var i = 1
                while (i < nbGalleryPages && !progress.isProcessHalted()) {
                    val pageDoc = getOnlineDocument(
                        content.galleryUrl + "/?p=" + i,
                        headers,
                        useHentoidAgent,
                        useWebviewAgent
                    )
                    pageDoc?.let { fetchPageUrls(it, pageUrls) }
                    progress.advance()
                    i++
                }
            }
            if (pageUrls.isEmpty()) throw ParseException("No picture pages have been found on the gallery")

            // 3- Open all pages and
            //    - grab the URL of the displayed image
            //    - grab the alternate URL of the "Click here if the image fails loading" link
            val result: MutableList<ImageFile> = ArrayList()
            result.add(ImageFile.newCover(content.coverImageUrl, StatusContent.SAVED))
            var order = 1
            for (pageUrl in pageUrls) {
                result.add(
                    ImageFile.fromPageUrl(
                        order++,
                        pageUrl,
                        StatusContent.SAVED,
                        pageUrls.size
                    )
                )
            }

            return result
        }

        private fun fetchPageUrls(doc: Document, pageUrls: MutableList<String>) {
            var imageLinks = doc.select(".gdtm a") // Normal thumbs
            if (imageLinks.isEmpty()) imageLinks = doc.select(".gdtl a") // Large thumbs
            if (imageLinks.isEmpty()) imageLinks = doc.select("#gdt a") // Universal, ID-based
            for (e in imageLinks) pageUrls.add(e.attr("href"))
        }

        private fun getDisplayedImageUrl(doc: Document): String {
            var element = doc.selectFirst("img#img")
            if (element != null) return getImgSrc(element)
            element = doc.selectFirst("#i3.img")
            return if (element != null) getImgSrc(element) else ""
        }

        private fun getFullImageUrl(doc: Document): String {
            val element = doc.selectFirst("a[href*=fullimg]")
            return if (element != null) element.attr("href") else ""
        }

        private fun getBackupPageUrl(doc: Document, queryUrl: String): String? {
            // "Click here if the image fails loading" link
            // -> add backup info to the image's downloadParams
            val elements = doc.select("#loadfail")
            if (!elements.isEmpty()) {
                val e = elements.first()
                if (e != null) {
                    var arg = e.attr("onclick")
                    // Get the argument between 's
                    val quoteBegin = arg.indexOf('\'')
                    val quoteEnd = arg.indexOf('\'', quoteBegin + 1)
                    arg = arg.substring(quoteBegin + 1, quoteEnd)
                    // Get the query URL
                    var backupUrl = queryUrl
                    backupUrl += if (backupUrl.contains("?")) "&" else "?"
                    backupUrl += "nl=$arg"
                    // Get the final URL
                    if (URLUtil.isValidUrl(backupUrl)) return backupUrl
                }
            }
            return null
        }

        @Throws(IOException::class)
        fun parseMpvPage(
            url: String,
            headers: List<Pair<String, String>>,
            useHentoidAgent: Boolean,
            useWebviewAgent: Boolean
        ): MpvInfo? {
            var result: MpvInfo? = null
            val doc = getOnlineDocument(url, headers, useHentoidAgent, useWebviewAgent)
                ?: throw ParseException("Unreachable MPV")

            val scripts: List<Element> = doc.select("script")
            for (script in scripts) {
                val scriptStr = script.toString()
                if (scriptStr.contains("pagecount")) {
                    result = MpvInfo()
                    val scriptLines = scriptStr.split("\n")
                    for (line in scriptLines) {
                        val parts = line.replace("  ", " ").replace(";", "").trim().split("=")
                        if (parts.size > 1) {
                            if (parts[0].contains("var gid")) {
                                result.gid =
                                    parts[1].replace("\"", "").trim().toInt()
                            } else if (parts[0].contains("var pagecount")) {
                                result.pagecount =
                                    parts[1].replace("\"", "").trim().toInt()
                            } else if (parts[0].contains("var mpvkey")) {
                                result.mpvkey = parts[1].replace("\"", "").trim()
                            } else if (parts[0].contains("var api_url")) {
                                result.apiUrl = parts[1].replace("\"", "").trim()
                            } else if (parts[0].contains("var imagelist")) {
                                val imgs: MutableList<EHentaiImageMetadata>? =
                                    JsonHelper.jsonToObject(
                                        parts[1].trim(), Types.newParameterizedType(
                                            MutableList::class.java,
                                            EHentaiImageMetadata::class.java
                                        )
                                    )
                                result.images = imgs ?: emptyList()
                            }
                        }
                    }
                    break
                }
            }
            return result
        }

    } // End of Companion Object

    @Throws(java.lang.Exception::class)
    private fun parseImageList(content: Content): List<ImageFile> {
        EventBus.getDefault().register(this)
        val result: List<ImageFile>
        try {
            // Retrieve and set cookies (optional; e-hentai can work without cookies even though certain galleries are unreachable)
            val cookieStr = getCookieStr(content)
            val headers: MutableList<Pair<String, String>> = java.util.ArrayList()
            headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
            headers.add(Pair(HEADER_REFERER_KEY, content.site.url))
            headers.add(Pair(HEADER_ACCEPT_KEY, "*/*"))

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
            val useHentoidAgent = Site.EHENTAI.useHentoidAgent()
            val useWebviewAgent = Site.EHENTAI.useWebviewAgent()

            val galleryDoc = getOnlineDocument(
                content.galleryUrl,
                headers,
                useHentoidAgent,
                useWebviewAgent
            ) ?: throw ParseException("Unreachable gallery page")
            //result = loadMpv("https://e-hentai.org/mpv/530350/8b3c7e4a21/", headers, useHentoidAgent, useWebviewAgent);

            // Detect if multipage viewer is on
            // NB : right now, the code detects if the MPV is present, even if e-h settings have it disabled
            val elements = galleryDoc.select(MPV_LINK_CSS)
            result = if (!elements.isEmpty()) {
                val mpvUrl = elements[0].attr("href")
                try {
                    loadMpv(
                        mpvUrl,
                        headers,
                        useHentoidAgent,
                        useWebviewAgent,
                        progress
                    )
                } catch (e: EmptyResultException) {
                    loadClassic(
                        content,
                        galleryDoc,
                        headers,
                        useHentoidAgent,
                        useWebviewAgent,
                        progress
                    )
                }
            } else {
                loadClassic(
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


    override fun parseImageList(
        content: Content,
        url: String
    ): List<ImageFile> {
        return parseImageList(content)
    }

    override fun parseImageList(onlineContent: Content, storedContent: Content): List<ImageFile> {
        return parseImageList(onlineContent)
    }

    override fun parseImagePage(
        url: String,
        requestHeaders: List<Pair<String, String>>
    ): Pair<String, String?> {
        return parseImagePage(url, requestHeaders, Site.EHENTAI)
    }

    override fun parseBackupUrl(
        url: String,
        requestHeaders: Map<String, String>,
        order: Int,
        maxPages: Int,
        chapter: Chapter?
    ): ImageFile? {
        return parseBackupUrl(
            url,
            Site.EHENTAI,
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