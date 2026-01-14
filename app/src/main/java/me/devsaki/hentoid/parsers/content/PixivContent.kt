package me.devsaki.hentoid.parsers.content

import androidx.core.net.toUri
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.sources.PixivServer
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.ACCEPT_ALL
import me.devsaki.hentoid.util.network.getCookies
import timber.log.Timber
import java.io.IOException

class PixivContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        var id = ""
        var entity = ""

        val uri = url.toUri()
        val urlParts = url.split("/")

        if (url.contains("user/bookmarks") || url.contains("/bookmarks/")) {
            entity = "bookmarks"
            id = if (url.contains("user/bookmarks")) uri.getQueryParameter("id") ?: ""
            else urlParts[urlParts.size - 3]
        } else {
            id = urlParts[urlParts.size - 1]
            if (id.contains("?")) id = id.take(id.indexOf("?"))
            entity = urlParts[urlParts.size - 2]
            when (entity) {
                "artworks", "illust" -> if (!isNumeric(id))
                    id = uri.getQueryParameter("illust_id") ?: ""

                "user", "users" -> if (!isNumeric(id)) id = uri.getQueryParameter("id") ?: ""

                else -> {}
            }
        }
        if (id.isEmpty()) return Content(site = Site.PIXIV, status = StatusContent.IGNORED)

        try {
            val cookieStr = getCookies(
                url,
                null,
                Site.PIXIV.useMobileAgent,
                Site.PIXIV.useHentoidAgent,
                Site.PIXIV.useWebviewAgent
            )
            val userAgent = getUserAgent(Site.PIXIV)
            when (entity) {
                "artworks", "illust" -> {
                    val metadata =
                        PixivServer.api.getIllustMetadata(id, cookieStr, ACCEPT_ALL, userAgent)
                            .execute().body()
                    if (metadata != null) return metadata.update(content, url, updateImages)
                }

                "series_content", "series" -> {
                    val seriesData =
                        PixivServer.api.getSeriesMetadata(id, cookieStr, ACCEPT_ALL, userAgent)
                            .execute().body()
                    if (seriesData != null) return seriesData.update(content, updateImages)
                }

                "user", "users" -> {
                    val userData =
                        PixivServer.api.getUserMetadata(id, cookieStr, ACCEPT_ALL, userAgent)
                            .execute().body()
                    if (userData != null) return userData.update(content, updateImages)
                }

                "bookmarks" -> {
                    val bookmarksData =
                        PixivServer.api.getUserBookmarks(id, cookieStr, ACCEPT_ALL, userAgent)
                            .execute().body()
                    if (bookmarksData != null) return bookmarksData.update(content, id, updateImages)
                }

                else -> {}
            }
        } catch (e: IOException) {
            Timber.e(e, "Error parsing content.")
        }
        return Content(site = Site.PIXIV, status = StatusContent.IGNORED)
    }
}