package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element

class HdPornComicsParser : BaseImageListParser() {
    companion object {
        fun parseImages(pages: List<Element>): List<String> {
            return pages.map { e -> ParseHelper.getImgSrc(e) }
                .map { s -> s.replace("thumbs", "uploads") }
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        // Fetch the book gallery page
        val doc = HttpHelper.getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        return parseImages(doc.select("figure a picture img").filterNotNull())
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing because no chapters for this source
        return emptyList()
    }
}