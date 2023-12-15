package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.ToonilyActivity
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.YoastGalleryMetadata
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.StringHelper
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
        content.setSite(Site.TOONILY)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        return if (GALLERY_PATTERN.matcher(url).find()) updateGallery(
            content,
            updateImages
        ) else updateSingleChapter(content, url, updateImages)
    }

    private fun updateSingleChapter(content: Content, url: String, updateImages: Boolean): Content {
        var title = NO_TITLE
        chapterTitle?.let {
            title = StringHelper.removeNonPrintableChars(it.text())
        }
        content.setTitle(title)
        val urlParts = url.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (urlParts.size > 1) content.uniqueSiteId =
            urlParts[urlParts.size - 2] else content.uniqueSiteId =
            urlParts[0]

        if (updateImages) {
            chapterImgs?.let {
                val imgUrls = it.mapNotNull { e ->
                    ParseHelper.getImgSrc(e)
                }.filterNot { str -> str.isEmpty() }.distinct()
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
        content.setCoverImageUrl(coverUrl)
        var title = NO_TITLE
        breadcrumbs?.let {
            if (it.isNotEmpty())
                title = StringHelper.removeNonPrintableChars(it[it.size - 1].text())
        }
        content.setTitle(title)
        content.populateUniqueSiteId()
        metadata?.let {
            if (it.childNodeSize() > 0) {
                try {
                    val galleryMeta = JsonHelper.jsonToObject(
                        it.childNode(0).toString(),
                        YoastGalleryMetadata::class.java
                    )
                    val publishDate = galleryMeta.datePublished // e.g. 2021-01-27T15:20:38+00:00
                    if (publishDate.isNotEmpty()) content.setUploadDate(
                        Helper.parseDatetimeToEpoch(publishDate, "yyyy-MM-dd'T'HH:mm:ssXXX")
                    )
                } catch (e: IOException) {
                    Timber.i(e)
                }
            }
        }
        val attributes = AttributeMap()
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artist, false, Site.TOONILY)
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, author, false, Site.TOONILY)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        return content
    }
}