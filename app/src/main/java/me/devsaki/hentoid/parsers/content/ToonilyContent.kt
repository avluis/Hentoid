package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.ToonilyActivity
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.YoastGalleryMetadata
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern

class ToonilyContent : BaseContentParser() {
    companion object {
        private val GALLERY_PATTERN = Pattern.compile(ToonilyActivity.GALLERY_PATTERN)
    }

    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var coverUrl: String

    @Selector(value = ".breadcrumb a")
    private var breadcrumbs: List<Element>? = null

    @Selector(value = "head script.yoast-schema-graph")
    private var metadata: Element? = null

    @Selector(value = ".author-content a")
    private var author: List<Element>? = null

    @Selector(value = ".artist-content a")
    private var artist: List<Element>? = null

    @Selector(value = "#chapter-heading")
    private var chapterTitle: Element? = null

    @Selector(value = ".reading-content img")
    private var chapterImgs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.TOONILY
        if (url.isEmpty()) return Content(status = StatusContent.IGNORED)
        content.setRawUrl(url)
        return if (GALLERY_PATTERN.matcher(url).find()) updateGallery(
            content,
            updateImages
        ) else updateSingleChapter(content, url, updateImages)
    }

    private fun updateSingleChapter(content: Content, url: String, updateImages: Boolean): Content {
        var title = NO_TITLE
        chapterTitle?.let {
            title = cleanup(it.text())
        }
        content.title = title
        val urlParts = url.split("/")
        if (urlParts.size > 1) content.uniqueSiteId =
            urlParts[urlParts.size - 2] else content.uniqueSiteId =
            urlParts[0]

        if (updateImages) {
            chapterImgs?.let { chpImg ->
                val imgUrls = chpImg.map { getImgSrc(it) }.filterNot { it.isEmpty() }.distinct()
                var coverUrl = ""
                if (imgUrls.isNotEmpty()) coverUrl = imgUrls[0]
                content.setImageFiles(
                    urlsToImageFiles(imgUrls, coverUrl, StatusContent.SAVED)
                )
                content.qtyPages = imgUrls.size
            }
        }
        return content
    }

    private fun updateGallery(content: Content, updateImages: Boolean): Content {
        content.coverImageUrl = coverUrl
        var title = NO_TITLE
        breadcrumbs?.let {
            if (it.isNotEmpty())
                title = cleanup(it[it.size - 1].text())
        }
        content.title = title
        content.populateUniqueSiteId()
        metadata?.let {
            if (it.childNodeSize() > 0) {
                try {
                    jsonToObject(
                        it.childNode(0).toString(),
                        YoastGalleryMetadata::class.java
                    )?.let { galleryMeta ->
                        val publishDate =
                            galleryMeta.getDatePublished() // e.g. 2021-01-27T15:20:38+00:00
                        if (publishDate.isNotEmpty()) content.uploadDate =
                            parseDatetimeToEpoch(publishDate, "yyyy-MM-dd'T'HH:mm:ssXXX")
                    }
                } catch (e: IOException) {
                    Timber.i(e)
                }
            }
        }
        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.ARTIST, artist, false, Site.TOONILY)
        parseAttributes(attributes, AttributeType.ARTIST, author, false, Site.TOONILY)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}