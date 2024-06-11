package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException

const val IMAGE_PATH = "https://static.hentaicdn.com/hentai"

class Hentai2ReadParser : BaseChapteredImageListParser() {
    companion object {
        @Throws(IOException::class)
        fun getDataFromScripts(scripts: List<Element>?): H2RInfo? {
            if (scripts != null) {
                for (e in scripts) {
                    if (e.childNodeSize() > 0 && e.childNode(0).toString().contains("'images' :")) {
                        val jsonStr = e.childNode(0).toString().replace("\n", "").trim { it <= ' ' }
                            .replace("var gData = ", "").replace("};", "}")
                        return jsonToObject(jsonStr, H2RInfo::class.java)
                    }
                }
            }
            return null
        }
    }

    data class H2RInfo(
        val title: String,
        val images: List<String>
    )


    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        var part = parts[parts.size - 1]
        if (part.isEmpty()) part = parts[parts.size - 2]
        return StringHelper.isNumeric(part)
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf(".nav-chapters a[href^=\$galleryUrl]"))
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            content.site.useHentoidAgent(),
            content.site.useWebviewAgent()
        )?.let { doc ->
            val scripts: List<Element> = doc.select("script")
            getDataFromScripts(scripts)?.let { info ->
                val imageUrls = info.images.map { s -> IMAGE_PATH + s }
                if (imageUrls.isNotEmpty()) return urlsToImageFiles(
                    imageUrls,
                    targetOrder,
                    StatusContent.SAVED,
                    1000,
                    chp
                )
            } ?: Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } ?: {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}