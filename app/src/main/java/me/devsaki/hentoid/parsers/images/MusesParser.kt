package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element

class MusesParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()

        // Fetch the book gallery page
        val doc = HttpHelper.getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        val thumbs: List<Element> = doc.select(".gallery img").filterNotNull()

        for (e in thumbs) {
            val src = ParseHelper.getImgSrc(e)
            if (src.isEmpty()) continue
            val thumbParts = src.split("/").toMutableList()
            if (thumbParts.size > 3) {
                // Large dimensions; there's also a medium variant available (fm)
                thumbParts[2] = "fl"
                val imgUrl =
                    Site.MUSES.url + "/" + thumbParts[1] + "/" + thumbParts[2] + "/" + thumbParts[3]
                result.add(imgUrl)
            }
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
}