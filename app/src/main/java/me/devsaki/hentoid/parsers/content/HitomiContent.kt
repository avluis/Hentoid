package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import java.util.regex.Pattern

private val hitomiCanonicalUrl = Pattern.compile("/galleries/[0-9]+\\.html")

class HitomiContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        content.site = Site.HITOMI

        // If given URL is canonical, set it as is
        if (hitomiCanonicalUrl.matcher(url).find()) {
            content.setRawUrl(url)
        } else { // If not, extract unique site ID (hitomi.la/category/stuff-<ID>.html#stuff)...
            var pathEndIndex = url.lastIndexOf("?")
            if (-1 == pathEndIndex) pathEndIndex = url.lastIndexOf("#")
            if (-1 == pathEndIndex) pathEndIndex = url.length
            val firstIndex = url.lastIndexOf("-", pathEndIndex)
            var lastIndex = url.lastIndexOf(".", pathEndIndex)
            if (-1 == lastIndex) lastIndex = pathEndIndex
            val uniqueId = url.substring(firstIndex + 1, lastIndex)
            content.uniqueSiteId = uniqueId

            // ...and forge canonical URL
            content.url = "/$uniqueId.html"
        }
        content.putAttributes(AttributeMap())
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}