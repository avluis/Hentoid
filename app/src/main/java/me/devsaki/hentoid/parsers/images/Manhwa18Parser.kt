package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.manhwa18.Manhwa18BookMetadata
import me.devsaki.hentoid.json.sources.manhwa18.Manhwa18ChapterMetadata
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream

class Manhwa18Parser : BaseChapteredImageListParser() {

    companion object {
        fun getDocData(doc: Document): String {
            return doc.body().ownText().ifBlank {
                StringEscapeUtils.unescapeHtml4(
                    doc.body().select("#app").attr("data-page")
                )
            }
        }

        fun parseChapters(doc: Document, contentId : Long): List<Chapter> {
            val data = getDocData(doc)
            jsonToObject(
                data,
                Manhwa18BookMetadata::class.java
            )?.let { return it.getChapters(contentId) }
            return emptyList()
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        val parts = url.split("/")
        return parts[parts.size - 1].startsWith("chap")
    }

    override fun getChapterSelector(): ChapterSelector {
        // Don't need to do anything as we directly override getChapters
        return ChapterSelector(emptyList(), "", "")
    }

    override fun getChapters(
        content: Content,
        galleryPage: Document
    ): List<Chapter> {
        return parseChapters(galleryPage, content.id)
    }

    @Throws(Exception::class)
    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        return getOnlineDocument(
            chp.url,
            headers ?: fetchHeaders(content),
            content.site.useHentoidAgent,
            content.site.useWebviewAgent
        )?.let { doc ->
            val data = getDocData(doc)
            jsonToObject(
                data,
                Manhwa18ChapterMetadata::class.java
            )?.let { metadata ->
                val stream: InputStream =
                    ByteArrayInputStream(metadata.props.chapterContent.toByteArray())
                val html = Jsoup.parse(stream, null, Site.MANHWA18.url)
                urlsToImageFiles(
                    html.select("img").map { getImgSrc(it) },
                    targetOrder,
                    StatusContent.SAVED,
                    1000,
                    chp
                )
            }
        } ?: run {
            Timber.i("Chapter parsing failed for %s : no response", chp.url)
            emptyList()
        }
    }
}