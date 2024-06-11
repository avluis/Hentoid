package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element

class HdPornComicsParser : BaseImageListParser() {
    companion object {
        fun parseImages(pages: List<Element>): List<String> {
            return pages.map { getImgSrc(it) }.map { it.replace("thumbs", "uploads") }
        }
    }

    override fun parseImages(content: Content): List<String> {
        // Fetch the book gallery page
        val doc = getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        return parseImages(doc.select("figure a picture img").filterNotNull())
    }
}