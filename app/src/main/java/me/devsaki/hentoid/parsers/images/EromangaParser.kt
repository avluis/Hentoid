package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.network.getOnlineDocument

class EromangaParser : BaseImageListParser() {
    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()

        getOnlineDocument(content.galleryUrl)?.let { doc ->
            doc.select(".entry-content img")?.let { imgs ->
                result.addAll(imgs.map { getImgSrc(it) })
            }
        }

        return result
    }
}