package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.HttpHelper.UriParts

class PururinParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()

        val headers = fetchHeaders(content)

        val doc = HttpHelper.getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.PURURIN.useHentoidAgent(),
            Site.PURURIN.useWebviewAgent()
        )
        if (doc != null) {
            // Get all thumb URLs and convert them to page URLs
            val imgSrc = doc.select(".gallery-preview img")
                .filterNotNull()
                .mapNotNull { e -> ParseHelper.getImgSrc(e) }
                .map { thumbUrl -> thumbToPage(thumbUrl) }
            result.addAll(imgSrc)
        }

        return result
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing; no chapters for this source
        return emptyList()
    }

    private fun thumbToPage(thumbUrl: String): String {
        val parts = UriParts(thumbUrl, true)
        val name = parts.fileNameNoExt
        parts.fileNameNoExt = name.substring(0, name.length - 1) // Remove the trailing 't'
        return parts.toUri()
    }
}