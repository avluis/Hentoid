package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.kemono.KemonoArtist
import me.devsaki.hentoid.parsers.Progressor
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.sources.KemonoServer
import me.devsaki.hentoid.util.download.DownloadRateLimiter
import me.devsaki.hentoid.util.download.DownloadRateLimiter.setRateLimit
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.getCookies
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class KemonoParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
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
            getPages(onlineContent)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    @Throws(Exception::class)
    private fun getPages(onlineContent: Content): List<ImageFile> {
        try {
            val useMobileAgent = Site.KEMONO.useMobileAgent
            val useHentoidAgent = Site.KEMONO.useHentoidAgent
            val useWebviewAgent = Site.KEMONO.useWebviewAgent
            val cookieStr = getCookies(
                onlineContent.galleryUrl, null,
                useMobileAgent, useHentoidAgent, useWebviewAgent
            )
            val userAgent = getUserAgent(Site.KEMONO)

            val parts = KemonoParts(onlineContent.url)

            setRateLimit(Site.KEMONO.requestsCapPerSecond.toLong())
            if (parts.isGallery) return parseGallery(
                onlineContent,
                onlineContent.galleryUrl,
                true,
                parts.service,
                parts.userId,
                parts.postId,
                cookieStr,
                userAgent
            ).imageList
            else if (parts.isUser) return parseUser(
                onlineContent,
                onlineContent.galleryUrl,
                parts.service,
                parts.userId,
                cookieStr,
                userAgent,
                this,
                true
            ).imageList
        } catch (e: Exception) {
            Timber.d(e)
            throw EmptyResultException(e.message ?: "")
        }
        DownloadRateLimiter.take() // One last delay before download phase
        return emptyList()
    }

    companion object {
        class KemonoParts(val url: String) {
            val service: String
            val userIdx: Int
            val postIdx: Int
            val userId: String
            val postId: String

            init {
                val urlParts = url.split("/")
                userIdx = urlParts.indexOf("user")
                postIdx = urlParts.indexOf("post")
                service = if (userIdx > -1) urlParts[userIdx - 1] else ""
                userId = if (userIdx > -1) urlParts[userIdx + 1] else ""
                postId = if (postIdx > -1) urlParts[postIdx + 1] else ""
            }

            val isGallery: Boolean
                get() = (userIdx > -1 && postIdx > -1)
            val isUser: Boolean
                get() = (-1 == postIdx && userIdx > -1)
        }

        fun parseGallery(
            content: Content,
            url: String,
            updateImages: Boolean,
            service: String,
            userId: String,
            postId: String,
            cookieStr: String,
            userAgent: String
        ): Content {
            try {
                // Get artist info
                val artist = getArtistAttr(service, userId, cookieStr, userAgent)
                DownloadRateLimiter.take()

                // Get gallery info
                KemonoServer.api.getGallery(
                    service = service,
                    userId = userId,
                    id = postId,
                    cookies = cookieStr,
                    accept = "text/css",
                    userAgent = userAgent
                ).execute().body()?.update(content, url, updateImages)?.let { result ->
                    // Add artist info
                    return result.addAttributes(listOf(artist.toAttribute()))
                }
                throw ParseException("No content found")
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
            return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
        }

        fun parseUser(
            content: Content,
            url: String,
            service: String,
            userId: String,
            cookieStr: String,
            userAgent: String,
            progressor: Progressor? = null,
            parseImages: Boolean = false
        ): Content {
            // Get galleries info
            try {
                // Get artist info
                progressor?.progressStart(content)
                val artist = getArtistAttr(service, userId, cookieStr, userAgent)
                DownloadRateLimiter.take()
                content.site = Site.KEMONO
                content.url = url.replace("/api/v1/", "/")
                    .replace("/posts", "/")
                content.status = StatusContent.SAVED
                content.uploadDate = 0L
                content.title = cleanup(artist.name)
                content.addAttributes(listOf(artist.toAttribute()))

                if (!parseImages) {
                    content.qtyPages = 0
                    content.setImageFiles(emptyList())
                    return content
                }

                // One result = one chapter, if it contains at least an usable picture (i.e. not exclusively MEGA links)
                val chapters = ArrayList<Chapter>()
                val chapterOrder = AtomicInteger(1)
                val pageOrder = AtomicInteger(1)
                var collectedGalleries = 0
                var nbResultsPerCall = 0
                var iteration = 0

                while (collectedGalleries < artist.postCount) {
                    KemonoServer.api.getArtistGalleries(
                        service = service,
                        userId = userId,
                        cookies = cookieStr,
                        accept = "text/css",
                        userAgent = userAgent,
                        offset = nbResultsPerCall * iteration++
                    ).execute().body()?.let { post ->
                        if (0 == nbResultsPerCall) nbResultsPerCall = post.count()
                        collectedGalleries += post.count()
                        post.forEachIndexed { _, result ->
                            chapters.add(
                                result.toChapter(
                                    artist.id,
                                    chapterOrder,
                                    pageOrder
                                )
                            )
                        }
                        progressor?.progressPlus(collectedGalleries * 1f / artist.postCount)
                        DownloadRateLimiter.take()
                    }
                }
                content.setChapters(chapters)
                content.qtyPages = chapters.sumOf { it.imageList.size }
                content.setImageFiles(chapters.flatMap { it.imageList })
                progressor?.progressComplete()
                return content
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
            return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
        }

        private fun getArtistAttr(
            service: String,
            userId: String,
            cookieStr: String,
            userAgent: String
        ): KemonoArtist {
            KemonoServer.api.getArtist(
                service = service,
                userId = userId,
                cookies = cookieStr,
                accept = "text/css",
                userAgent = userAgent
            ).execute().body()?.let { return it }
            throw ParseException("No artist found")
        }
    }
}