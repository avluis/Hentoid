package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.activities.sources.KemonoActivity.Companion.DOMAIN_FILTER
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.kemono.KemonoArtist
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.sources.KemonoServer
import me.devsaki.hentoid.util.download.DownloadRateLimiter.setRateLimit
import me.devsaki.hentoid.util.download.DownloadRateLimiter.take
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.image.isSupportedImage
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
            getPages(onlineContent, storedContent)
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }

    @Throws(Exception::class)
    private fun getPages(onlineContent: Content, storedContent: Content?): List<ImageFile> {
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
                userAgent
            ).imageList
        } catch (e: Exception) {
            Timber.d(e)
            throw EmptyResultException(e.message ?: "")
        }
        take() // One last delay before download phase
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
                    enrichWithArtist(result, service, userId, cookieStr, userAgent)
                    return result
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
            userAgent: String
        ): Content {
            // Get galleries info
            try {
                KemonoServer.api.getArtistGalleries(
                    service = service,
                    userId = userId,
                    cookies = cookieStr,
                    accept = "text/css",
                    userAgent = userAgent
                ).execute().body()?.let { post ->
                    content.site = Site.KEMONO
                    content.url = url.replace("/api/v1/", "/")
                        .replace("/posts", "/")
                    content.status = StatusContent.SAVED
                    content.uploadDate = 0L
                    val artist = enrichWithArtist(content, service, userId, cookieStr, userAgent)
                    content.title = cleanup(artist.name)

                    // One result = one chapter, if it contains at least an usable picture (i.e. not exclusively MEGA links)
                    val nbPagesTotal = post
                        .flatMap { it.attachments }
                        .filter { isSupportedImage(it.path ?: "") }
                        .distinct()
                        .count()
                    val chapters = ArrayList<Chapter>()
                    val chapterOrder = AtomicInteger(1)
                    val pageOrder = AtomicInteger(1)
                    post.forEachIndexed { index, result ->
                        chapters.add(
                            result.toChapter(
                                artist.id,
                                chapterOrder,
                                pageOrder,
                                nbPagesTotal
                            )
                        )
                    }
                    content.setChapters(chapters)
                    content.qtyPages = chapters.sumOf { it.imageList.size }
                    content.setImageFiles(chapters.flatMap { it.imageList })
                    return content
                }
                throw ParseException("No content found")
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
            return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
        }

        private fun enrichWithArtist(
            content: Content,
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
            ).execute().body()?.let { artist ->
                content.addAttributes(
                    listOf(
                        Attribute(
                            AttributeType.ARTIST,
                            artist.name,
                            "https://$DOMAIN_FILTER/${artist.service}/user/${artist.id}",
                            Site.KEMONO
                        )
                    )
                )
                return artist
            }
            throw ParseException("No artist found")
        }
    }
}