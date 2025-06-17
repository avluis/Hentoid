package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.sources.KemonoServer
import me.devsaki.hentoid.util.network.ACCEPT_ALL
import me.devsaki.hentoid.util.network.getCookies
import timber.log.Timber
import java.io.IOException

class KemonoContent : BaseContentParser() {

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val urlParts = url.split("/")
        if (urlParts.size > 7) {
            val offset = if (urlParts[3] == "api") 2 else 0
            val cookieStr = getCookies(
                url, null,
                Site.KEMONO.useMobileAgent, Site.KEMONO.useHentoidAgent, Site.KEMONO.useWebviewAgent
            )
            val userAgent = getUserAgent(Site.KEMONO)
            try {
                val result = KemonoServer.api.getGallery(
                    service = urlParts[3 + offset],
                    userId = urlParts[5 + offset],
                    id = urlParts[7 + offset],
                    cookies = cookieStr,
                    accept = ACCEPT_ALL,
                    userAgent = userAgent
                ).execute().body()?.update(content, url, updateImages)
                if (result != null) {
                    KemonoServer.api.getArtist(
                        service = urlParts[3 + offset],
                        id = urlParts[7 + offset],
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
        }
        return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
    }
}