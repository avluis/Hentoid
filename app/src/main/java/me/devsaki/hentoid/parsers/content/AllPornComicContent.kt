package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.APC_GALLERY_PATTERN
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

private val GALLERY_PATTERN = Pattern.compile(APC_GALLERY_PATTERN)

class AllPornComicContent : BaseContentParser() {

    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var coverUrl: String

    @Selector(value = "head title")
    private var title: Element? = null

    @Selector(value = "head script.yoast-schema-graph")
    private var metadata: Element? = null

    @Selector(value = ".post-content a[href*='characters']")
    private var characterTags: List<Element>? = null

    @Selector(value = ".post-content a[href*='series']")
    private var seriesTags: List<Element>? = null

    @Selector(value = ".post-content a[href*='porncomic-artist']")
    private var artistsTags: List<Element>? = null

    @Selector(value = ".post-content a[href*='porncomic-genre']")
    private var tags: List<Element>? = null

    @Selector(value = "[class^=page-break] img")
    private var chapterImages: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.ALLPORNCOMIC
        if (url.isEmpty()) return Content(status = StatusContent.IGNORED)
        content.setRawUrl(url)
        content.coverImageUrl = coverUrl

        content.title = cleanup(title?.text())
            .replace(" - AllPornComic", "")
            .replace(" Porn Comic", "")

        metadata?.apply {
            if (childNodeSize() > 0) {
                try {
                    jsonToObject(
                        childNode(0).toString(),
                        YoastGalleryMetadata::class.java
                    )?.let { galleryMeta ->
                        val publishDate =
                            galleryMeta.datePublished // e.g. 2021-01-27T15:20:38+00:00
                        if (publishDate.isNotEmpty()) content.uploadDate =
                            parseDatetimeToEpoch(publishDate, "yyyy-MM-dd'T'HH:mm:ssXXX")
                    }
                } catch (e: IOException) {
                    Timber.i(e)
                }
            }
        }
        return if (GALLERY_PATTERN.matcher(url).find())
            updateGallery(content, updateImages)
        else updateSingleChapter(content, updateImages)
    }

    private fun updateSingleChapter(content: Content, updateImages: Boolean): Content {
        if (updateImages) {
            chapterImages?.let { chImg ->
                val imgUrls = chImg.map { getImgSrc(it) }.filterNot { it.isEmpty() }
                content.setImageFiles(
                    urlsToImageFiles(
                        imgUrls,
                        coverUrl,
                        StatusContent.SAVED
                    )
                )
                content.qtyPages = imgUrls.size // Don't count the cover
            }
        }
        return content
    }

    private fun updateGallery(content: Content, updateImages: Boolean): Content {
        val attributes = AttributeMap()
        parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characterTags,
            false,
            Site.ALLPORNCOMIC
        )
        parseAttributes(
            attributes,
            AttributeType.SERIE,
            seriesTags,
            false,
            Site.ALLPORNCOMIC
        )
        parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artistsTags,
            false,
            Site.ALLPORNCOMIC
        )
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.ALLPORNCOMIC)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}