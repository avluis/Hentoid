package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.sources.KemonoServer
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.ACCEPT_ALL
import me.devsaki.hentoid.util.network.getCookies
import timber.log.Timber
import java.io.IOException

class KemonoContent : BaseContentParser() {

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val urlParts = url.split("/")
        val userIdx = urlParts.indexOf("user")
        val postIdx = urlParts.indexOf("post")
        val service = if (userIdx > -1) urlParts[userIdx - 1] else ""
        val userId = if (userIdx > -1) urlParts[userIdx + 1] else ""
        val postId = if (postIdx > -1) urlParts[postIdx + 1] else ""

        val isGallery = userIdx > -1 && postIdx > -1
        val isUser = -1 == postIdx && userIdx > -1

        val cookieStr = getCookies(
            url, null,
            Site.KEMONO.useMobileAgent, Site.KEMONO.useHentoidAgent, Site.KEMONO.useWebviewAgent
        )
        val userAgent = getUserAgent(Site.KEMONO)

        if (isGallery) return parseGallery(
            content,
            url,
            updateImages,
            service,
            userId,
            postId,
            cookieStr,
            userAgent
        )
        else if (isUser) return parseUser(content, url, service, userId, cookieStr, userAgent)

        return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
    }

    private fun parseGallery(
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

    private fun parseUser(
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
            ).execute().body()?.update(content, url)?.let { return it }
                ?: throw ParseException("No content found")
        } catch (e: IOException) {
            Timber.e(e, "Error parsing content.")
        }
        return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
    }
}