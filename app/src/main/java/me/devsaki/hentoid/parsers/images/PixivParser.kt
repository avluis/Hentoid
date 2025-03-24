package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getExtraChaptersbyId
import me.devsaki.hentoid.parsers.getExtraChaptersbyUrl
import me.devsaki.hentoid.parsers.getMaxChapterOrder
import me.devsaki.hentoid.parsers.getMaxImageOrder
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.retrofit.sources.PixivServer
import me.devsaki.hentoid.util.KEY_DL_PARAMS_NB_CHAPTERS
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.download.DownloadRateLimiter.setRateLimit
import me.devsaki.hentoid.util.download.DownloadRateLimiter.take
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.waitBlocking429
import me.devsaki.hentoid.util.parseDownloadParams
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

private const val MAX_QUERY_WINDOW = 30

class PixivParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageList is overriden directly
        return emptyList()
    }

    @Throws(Exception::class)
    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        EventBus.getDefault().register(this)
        processedUrl = onlineContent.galleryUrl
        val result: List<ImageFile> = try {
            getPages(onlineContent, storedContent)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    @Throws(Exception::class)
    private fun getPages(onlineContent: Content, storedContent: Content?): List<ImageFile> {
        try {
            val useMobileAgent = Site.PIXIV.useMobileAgent
            val useHentoidAgent = Site.PIXIV.useHentoidAgent
            val useWebviewAgent = Site.PIXIV.useWebviewAgent
            val cookieStr = getCookies(
                onlineContent.galleryUrl, null,
                useMobileAgent, useHentoidAgent, useWebviewAgent
            )
            val userAgent = getUserAgent(Site.PIXIV)
            val acceptAll = "*/*"

            // API calls seem to be protected against request spam; 2 is arbitrary
            setRateLimit(2)
            if (onlineContent.url.contains("/series/")) return parseSeries(
                onlineContent,
                storedContent,
                cookieStr,
                acceptAll,
                userAgent
            ) else if (onlineContent.url.contains("/artworks/")) return parseIllust(
                onlineContent,
                cookieStr,
                acceptAll,
                userAgent
            ) else if (onlineContent.url.contains("users/")) return parseUser(
                onlineContent,
                storedContent,
                cookieStr,
                acceptAll,
                userAgent
            )
        } catch (e: Exception) {
            Timber.d(e)
            throw EmptyResultException(e.message ?: "")
        }
        take() // One last delay before download phase
        return emptyList()
    }

    @Throws(Exception::class)
    private fun parseIllust(
        content: Content,
        cookieStr: String,
        acceptAll: String,
        userAgent: String
    ): List<ImageFile> {
        take()
        val galleryMetadata =
            PixivServer.api.getIllustPages(content.uniqueSiteId, cookieStr, acceptAll, userAgent)
                .execute().body()
        if (null == galleryMetadata || galleryMetadata.error == true) {
            var message = ""
            if (galleryMetadata != null) message = galleryMetadata.message ?: ""
            throw EmptyResultException(message)
        }
        return urlsToImageFiles(
            galleryMetadata.getPageUrls(),
            content.coverImageUrl,
            StatusContent.SAVED
        )
    }

    @Throws(Exception::class)
    private fun parseSeries(
        onlineContent: Content,
        storedContent: Content?,
        cookieStr: String,
        acceptAll: String,
        userAgent: String
    ): List<ImageFile> {
        val seriesIdParts =
            onlineContent.uniqueSiteId.split("/")
        var seriesId = seriesIdParts[seriesIdParts.size - 1]
        if (seriesId.contains("?")) {
            seriesId = seriesId.substring(0, seriesId.indexOf("?"))
        }

        // Retrieve the number of Illusts
        val nbChaptersStr =
            parseDownloadParams(onlineContent.downloadParams)[KEY_DL_PARAMS_NB_CHAPTERS]
        require(nbChaptersStr != null) { "Chapter count not saved" }
        require(isNumeric(nbChaptersStr)) { "Chapter count not saved" }
        val nbChapters = nbChaptersStr.toInt()

        // List all Illust IDs (API is paged, hence the loop)
        val chapters: MutableList<Chapter> = ArrayList()
        while (chapters.size < nbChapters) {
            if (processHalted.get()) break
            val chaptersToRead = (nbChapters - chapters.size).coerceAtMost(MAX_QUERY_WINDOW)
            take()
            val seriesContentMetadata = PixivServer.api.getSeriesIllusts(
                seriesId,
                chaptersToRead,
                chapters.size,
                cookieStr,
                acceptAll,
                userAgent
            ).execute().body()
            if (null == seriesContentMetadata || true == seriesContentMetadata.error) {
                var message: String? = "Unreachable series illust"
                if (seriesContentMetadata != null) message = seriesContentMetadata.message
                throw IllegalArgumentException(message)
            }
            chapters.addAll(seriesContentMetadata.getChapters(onlineContent.id))
        }
        // Put back chapters in reading order
        chapters.reverse()


        // If the stored content has chapters already, save them for comparison
        var storedChapters: List<Chapter>? = null
        if (storedContent != null) {
            storedChapters = storedContent.chapters
            storedChapters = storedChapters.toMutableList() // Work on a copy
        }
        if (null == storedChapters) storedChapters = emptyList()

        // Use chapter folder as a differentiator (as the whole URL may evolve)
        val extraChapters = getExtraChaptersbyUrl(storedChapters, chapters)
        progressStart(onlineContent, storedContent)

        // Start numbering extra images right after the last position of stored and chaptered images
        var imgOffset = getMaxImageOrder(storedChapters) + 1

        // Retrieve all Illust detailed info
        val result: MutableList<ImageFile> = ArrayList()
        result.add(ImageFile.newCover(onlineContent.coverImageUrl, StatusContent.SAVED))
        val attrs: MutableSet<Attribute> = HashSet()
        extraChapters.forEachIndexed { index, ch ->
            if (processHalted.get()) return@forEachIndexed
            take()
            val illustMetadata =
                PixivServer.api.getIllustMetadata(ch.uniqueId, cookieStr, acceptAll, userAgent)
                    .execute().body()
            if (null == illustMetadata || illustMetadata.error) {
                var message: String? = "Unreachable illust"
                if (illustMetadata != null) message = illustMetadata.message
                throw IllegalArgumentException(message)
            }
            val chapterAttrs = illustMetadata.getAttributes()
            attrs.addAll(chapterAttrs)
            val chapterImages = illustMetadata.getImageFiles()
            for (img in chapterImages) {
                img.order = imgOffset++
                img.setChapter(ch)
                img.computeName(4)
            }
            result.addAll(chapterImages)
            progressPlus((index + 1f) / extraChapters.size)
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()
        onlineContent.putAttributes(attrs)
        progressComplete()
        return result
    }

    @Throws(Exception::class)
    private fun parseUser(
        onlineContent: Content,
        storedContent: Content?,
        cookieStr: String,
        acceptAll: String,
        userAgent: String
    ): List<ImageFile> {
        val userIdParts =
            onlineContent.uniqueSiteId.split("/")
        var userId = userIdParts[userIdParts.size - 1]
        if (userId.contains("?")) {
            userId = userId.substring(0, userId.indexOf("?"))
        }

        // Retrieve the list of Illusts IDs (=chapters)
        var waited = 0
        take()
        var userIllustResp =
            PixivServer.api.getUserIllusts(userId, cookieStr, acceptAll, userAgent).execute()
        if (waitBlocking429(
                userIllustResp,
                Settings.http429DefaultDelaySecs * 1000
            )
        ) {
            waited++
            userIllustResp =
                PixivServer.api.getUserIllusts(userId, cookieStr, acceptAll, userAgent).execute()
        }
        require(userIllustResp.code() < 400) {
            String.format(
                "Unreachable user illusts : code=%s (%s) [%d]",
                userIllustResp.code(),
                userIllustResp.message(),
                waited
            )
        }
        val userIllustsMetadata = userIllustResp.body()
        if (null == userIllustsMetadata || userIllustsMetadata.isError()) {
            var message: String? = "Unreachable user illusts"
            if (userIllustsMetadata != null) message = userIllustsMetadata.getMessage()
            throw IllegalArgumentException(message)
        }

        // Detect extra chapters
        var illustIds = userIllustsMetadata.getIllustIds()
        var storedChapters: List<Chapter>? = null
        if (storedContent != null) {
            storedChapters = storedContent.chapters
            storedChapters = storedChapters.toMutableList()
        }
        if (null == storedChapters) storedChapters = emptyList()
        else illustIds = getExtraChaptersbyId(storedChapters, illustIds)

        // Work on detected extra chapters
        progressStart(onlineContent, storedContent)

        // Start numbering extra images & chapters right after the last position of stored and chaptered images & chapters
        var imgOffset = getMaxImageOrder(storedChapters) + 1
        var chpOffset = getMaxChapterOrder(storedChapters) + 1

        // Cycle through all Illusts
        val result: MutableList<ImageFile> = ArrayList()
        result.add(ImageFile.newCover(onlineContent.coverImageUrl, StatusContent.SAVED))
        val attrs: MutableSet<Attribute> = HashSet()
        illustIds.forEachIndexed { index, illustId ->
            waited = 0
            take()
            if (processHalted.get()) return@forEachIndexed
            var illustResp =
                PixivServer.api.getIllustMetadata(illustId, cookieStr, acceptAll, userAgent)
                    .execute()
            while (waitBlocking429(
                    illustResp,
                    Settings.http429DefaultDelaySecs * 1000
                ) && waited < 2
            ) {
                waited++
                illustResp =
                    PixivServer.api.getIllustMetadata(illustId, cookieStr, acceptAll, userAgent)
                        .execute()
            }
            require(illustResp.code() < 400) {
                String.format(
                    "Unreachable illust : code=%s (%s) [%d - %d]",
                    illustResp.code(),
                    illustResp.message(),
                    index,
                    waited
                )
            }
            val illustMetadata = illustResp.body()
            if (null == illustMetadata || illustMetadata.error) {
                val message: String? =
                    if (illustMetadata != null) illustMetadata.message else "no metadata"
                throw IllegalArgumentException(String.format("Unreachable illust : %s", message))
            }
            val chapterAttrs = illustMetadata.getAttributes()
            attrs.addAll(chapterAttrs)
            val chp = Chapter(
                order = chpOffset++,
                url = illustMetadata.getUrl(),
                name = illustMetadata.getTitle(),
                uniqueId = illustMetadata.getId()
            )
            chp.setContentId(onlineContent.id)
            val chapterImages = illustMetadata.getImageFiles()
            for (img in chapterImages) {
                img.order = imgOffset++
                img.setChapter(chp)
                img.computeName(4)
            }
            result.addAll(chapterImages)
            progressPlus((index + 1f) / illustIds.size)
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw PreparationInterruptedException()
        onlineContent.putAttributes(attrs)
        onlineContent.putAttributes(attrs)
        progressComplete()
        return result
    }
}