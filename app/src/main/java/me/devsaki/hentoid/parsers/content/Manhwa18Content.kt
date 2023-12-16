package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.Manhwa18Activity
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.StringHelper
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.regex.Pattern

class Manhwa18Content : BaseContentParser() {
    companion object {
        private val GALLERY_PATTERN = Pattern.compile(Manhwa18Activity.GALLERY_PATTERN)
    }

    @Selector(value = ".series-cover div div", attr = "style", defValue = "")
    private lateinit var cover: String

    @Selector(value = ".series-name a")
    private var title: Element? = null

    @Selector(value = ".series-information a[href*=tac-gia]")
    private var artists: List<Element>? = null

    @Selector(value = ".series-information a[href*=genre]")
    private var tags: List<Element>? = null

    @Selector(value = "meta[property=og:title]", attr = "content", defValue = "")
    private lateinit var chapterTitle: String

    @Selector(value = "#chapter-content img")
    private var chapterImgs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.MANHWA18)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        return if (GALLERY_PATTERN.matcher(url).find()) updateGallery(
            content,
            updateImages
        ) else updateSingleChapter(content, url, updateImages)
    }

    private fun updateSingleChapter(
        content: Content,
        url: String,
        updateImages: Boolean
    ): Content {
        var title = StringHelper.removeNonPrintableChars(chapterTitle)
        title = StringEscapeUtils.unescapeHtml4(title)
        content.setTitle(title)
        val urlParts = url.split("/")
        if (urlParts.size > 1) content.uniqueSiteId = urlParts[urlParts.size - 2]
        else content.uniqueSiteId = urlParts[0]
        if (updateImages) {
            chapterImgs?.let {
                val imgUrls = it.mapNotNull { e -> ParseHelper.getImgSrc(e) }
                var coverUrl = ""
                if (imgUrls.isNotEmpty()) coverUrl = imgUrls[0]
                content.setImageFiles(
                    ParseHelper.urlsToImageFiles(
                        imgUrls,
                        coverUrl,
                        StatusContent.SAVED
                    )
                )
                content.setQtyPages(imgUrls.size)
            }
        }
        return content
    }

    private fun updateGallery(content: Content, updateImages: Boolean): Content {
        cover = cover.replace("background-image:", "")
            .replace("url('", "")
            .replace("')", "")
            .replace(";", "")
            .trim()
        content.setCoverImageUrl(cover)
        var titleStr: String? = NO_TITLE
        title?.let {
            titleStr = StringHelper.removeNonPrintableChars(it.text())
            titleStr = ParseHelper.removeTextualTags(titleStr)
        }
        content.setTitle(titleStr)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        val attributes = AttributeMap()
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.MANHWA18)
        ParseHelper.parseAttributes(
            attributes,
            AttributeType.TAG,
            tags,
            false,
            "badge",
            Site.MANHWA18
        )
        content.putAttributes(attributes)
        return content
    }
}