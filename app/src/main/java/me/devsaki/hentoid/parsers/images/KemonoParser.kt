package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.sources.KemonoServer
import me.devsaki.hentoid.util.download.DownloadRateLimiter.setRateLimit
import me.devsaki.hentoid.util.download.DownloadRateLimiter.take
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.ACCEPT_ALL
import me.devsaki.hentoid.util.network.getCookies
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException

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
                val result = KemonoServer.api.getGallery(
                    service = service,
                    userId = userId,
                    id = postId,
                    cookies = cookieStr,
                    accept = ACCEPT_ALL,
                    userAgent = userAgent
                ).execute().body()?.update(content, url, updateImages)
                if (result != null) {
                    KemonoServer.api.getArtist(
                        service = service,
                        userId = userId,
                        cookies = cookieStr,
                        accept = ACCEPT_ALL,
                        userAgent = userAgent
                    ).execute().body()?.let { artist ->
                        content.addAttributes(
                            listOf(
                                Attribute(
                                    AttributeType.ARTIST,
                                    artist.name,
                                    "https://kemono.su/${artist.service}/user/${artist.id}",
                                    Site.KEMONO
                                )
                            )
                        )
                    }
                    return content
                }
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
            try {
                KemonoServer.api.getArtistGalleries(
                    service = service,
                    userId = userId,
                    cookies = cookieStr,
                    accept = ACCEPT_ALL,
                    userAgent = userAgent
                ).execute().body()?.update(content, url)?.let {
                    return it
                } ?: throw ParseException("No content found")
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
            return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
        }
    }
}