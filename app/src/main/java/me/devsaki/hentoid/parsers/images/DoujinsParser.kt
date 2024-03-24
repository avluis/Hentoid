package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element

class DoujinsParser : BaseImageListParser() {
    companion object {
        fun parseImages(images: List<Element>): List<String> {
            return images.mapNotNull { e -> e.attr("data-file") }
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        // Fetch the book gallery page
        val doc = getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        return parseImages(doc.select("img.doujin"))
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing as this source doesn't have chapters
        return emptyList()
    }
}