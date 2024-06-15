package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException

class EdoujinParser : BaseChapteredImageListParser() {

    data class EdoujinInfo(private val sources: List<EdoujinSource>?) {
        fun getImages(): List<String> {
            val result: MutableList<String> = ArrayList()
            if (sources != null) {
                for (s in sources) if (s.images != null) result.addAll(s.images)
            }
            return result
        }
    }

    data class EdoujinSource(
        val images: List<String>?
    )

    companion object {
        @Throws(IOException::class)
        fun getDataFromScripts(scripts: List<Element>?): EdoujinInfo? {
            if (scripts != null) {
                for (e in scripts) {
                    if (e.childNodeSize() > 0
                        && e.childNode(0).toString().contains("\"noimagehtml\"")
                    ) {
                        var jsonStr = e.childNode(0).toString()
                            .replace("\n", "").trim()
                            .replace("});", "}")
                        jsonStr = jsonStr.substring(jsonStr.indexOf('{'))
                        return jsonToObject(jsonStr, EdoujinInfo::class.java)
                    }
                }
            }
            return null
        }
    }


    override fun isChapterUrl(url: String): Boolean {
        var parts = url.split("/")
        parts = parts[parts.size - 1].split("-")
        return isNumeric(parts[parts.size - 1])
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf("#chapterlist .eph-num a"))
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
                val imageUrls = info.getImages()
                if (imageUrls.isNotEmpty()) {
                    progressPlus(1f)
                    return urlsToImageFiles(
                        imageUrls,
                        targetOrder,
                        StatusContent.SAVED,
                        1000,
                        chp
                    )
                }
            } ?: Timber.i("Chapter parsing failed for %s : no pictures found", chp.url)
        } ?: {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
        }
        return emptyList()
    }
}