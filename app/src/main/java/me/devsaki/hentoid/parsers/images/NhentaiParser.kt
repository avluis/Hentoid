package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element

class NhentaiParser : BaseImageListParser() {
    companion object {
        fun parseImages(content: Content, thumbs: List<Element>): List<String> {
            val coverParts = content.coverImageUrl.split("/")
            val mediaId = coverParts[coverParts.size - 2]
            // We infer the whole book is stored on the same server
            val serverUrl = "https://i.nhentai.net/galleries/$mediaId/"
            val result: MutableList<String> = ArrayList()
            var index = 1
            for (e in thumbs) {
                val s = ParseHelper.getImgSrc(e)
                if (s.isEmpty()) continue
                result.add(serverUrl + index++ + "." + FileHelper.getExtension(s))
            }
            return result
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        // Fetch the book gallery page
        val doc = HttpHelper.getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        val thumbs = doc.select("#thumbnail-container img[data-src]").filterNotNull()

        return parseImages(content, thumbs)
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