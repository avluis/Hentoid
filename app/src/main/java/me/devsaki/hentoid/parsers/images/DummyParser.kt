package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import org.apache.commons.lang3.NotImplementedException

class DummyParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        return content.imageList.map { img -> img.url }
    }

    @Throws(Exception::class)
    override fun parseImages(
        chapterUrl: String,
        downloadParams: String,
        headers: List<Pair<String, String>>
    ): List<String> {
        throw NotImplementedException()
    }
}