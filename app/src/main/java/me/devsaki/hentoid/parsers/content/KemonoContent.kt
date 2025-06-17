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
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException

class KemonoContent : BaseContentParser() {
    @Selector(value = ".post__title", defValue = "")
    private lateinit var title: String

    @Selector(value = ".post__user-name", defValue = "")
    private lateinit var artist: Element

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val galleryUrlParts = url.split("/")
        if (galleryUrlParts.size > 7) {
            val cookieStr = getCookies(
                url, null,
                Site.KEMONO.useMobileAgent, Site.KEMONO.useHentoidAgent, Site.KEMONO.useWebviewAgent
            )
            val userAgent = getUserAgent(Site.KEMONO)
            try {
                val result = KemonoServer.api.getGallery(
                    service = galleryUrlParts[3],
                    userId = galleryUrlParts[5],
                    id = galleryUrlParts[7],
                    cookies = cookieStr,
                    accept = ACCEPT_ALL,
                    userAgent = userAgent
                ).execute().body()?.update(content, url, updateImages)
                if (result != null) {
                    KemonoServer.api.getArtist(
                        service = galleryUrlParts[3],
                        id = galleryUrlParts[7],
                        cookies = cookieStr,
                        accept = ACCEPT_ALL,
                        userAgent = userAgent
                    ).execute().body()?.let { artist ->
                        content.addAttributes(
                            listOf(
                                Attribute(
                                    AttributeType.ARTIST,
                                    artist.name,
                                    "https://kemono.su/fanbox/user/${artist.id}",
                                    Site.KEMONO
                                )
                            )
                        )
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content.")
            }
        }
        return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
    }
}