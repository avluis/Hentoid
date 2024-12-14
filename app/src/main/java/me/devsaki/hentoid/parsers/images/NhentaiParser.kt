package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element

class NhentaiParser : BaseImageListParser() {
    companion object {
        fun parseImages(content: Content, thumbs: List<Element>): List<String> {
            val coverParts = content.coverImageUrl.split("/")
            val mediaId = coverParts[coverParts.size - 2]
            // We infer the whole book is stored on the same server
            val serverUrl = "https://i2.nhentai.net/galleries/$mediaId/"
            val result: MutableList<String> = ArrayList()
            var index = 1
            for (e in thumbs) {
                val s = getImgSrc(e)
                if (s.isEmpty()) continue
                result.add(serverUrl + index++ + "." + getNhGalleryExtension(s))
            }
            return result
        }

        private fun getNhGalleryExtension(uri: String): String {
            val name = uri.substring(uri.lastIndexOf("/") + 1)
            if (!name.contains(".")) return ""
            val parts = name.split(".")
            // Classic case
            if (2 == parts.size) return parts[1].lowercase()
            // Nhentai-specific stuff (name.jpg.webp) => last but one is the correct one
            return parts[parts.size - 2].lowercase()
        }
    }

    override fun parseImages(content: Content): List<String> {
        // Fetch the book gallery page
        val doc = getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        val thumbs = doc.select("#thumbnail-container img[data-src]").filterNotNull()
        return parseImages(content, thumbs)
    }
}