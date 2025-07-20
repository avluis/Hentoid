package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.parsers.images.KemonoParser
import me.devsaki.hentoid.parsers.images.KemonoParser.Companion.parseGallery
import me.devsaki.hentoid.parsers.images.KemonoParser.Companion.parseUser
import me.devsaki.hentoid.util.network.getCookies

class KemonoContent : BaseContentParser() {

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val parts = KemonoParser.Companion.KemonoParts(url)

        val cookieStr = getCookies(
            url, null,
            Site.KEMONO.useMobileAgent, Site.KEMONO.useHentoidAgent, Site.KEMONO.useWebviewAgent
        )
        val userAgent = getUserAgent(Site.KEMONO)

        if (parts.isGallery) return parseGallery(
            content,
            url,
            updateImages,
            parts.service,
            parts.userId,
            parts.postId,
            cookieStr,
            userAgent
        )
        else if (parts.isUser) return parseUser(
            content,
            url,
            parts.service,
            parts.userId,
            cookieStr,
            userAgent
        )

        return Content(site = Site.KEMONO, status = StatusContent.IGNORED)
    }
}