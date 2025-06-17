package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineDocument

class KemonoParser : BaseImageListParser() {

    override fun parseImagePage(
        url: String,
        requestHeaders: List<Pair<String, String>>
    ): Pair<String, String?> {
        getOnlineDocument(
            url,
            requestHeaders,
            Site.KEMONO.useHentoidAgent,
            Site.KEMONO.useWebviewAgent
        )?.let { doc ->
            doc.selectFirst(".content-image")?.let {
                return Pair(getImgSrc(it), "")
            }
        }
        return Pair("", null)
    }

    override fun parseImages(content: Content): List<String> {
        // Already overrides parseImageListImpl
        return emptyList()
    }
}