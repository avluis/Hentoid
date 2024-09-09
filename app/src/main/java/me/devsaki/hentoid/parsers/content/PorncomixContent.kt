package me.devsaki.hentoid.parsers.content

import com.squareup.moshi.JsonDataException
import me.devsaki.hentoid.activities.sources.PCX_GALLERY_PATTERN
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.YoastGalleryMetadata
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern

private val GALLERY_PATTERN = Pattern.compile(PCX_GALLERY_PATTERN)

class PorncomixContent : BaseContentParser() {
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var coverUrl: String

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = "head script.yoast-schema-graph")
    private var metadata: Element? = null

    @Selector(value = ".wp-manga-tags-list a[href*='tag']")
    private var mangaTags: List<Element>? = null

    @Selector(value = ".item-tags a[href*='tag']")
    private var galleryTags: List<Element>? = null

    @Selector(value = ".bb-tags a[href*='label']")
    private var zoneTags: List<Element>? = null

    @Selector(value = ".video-tags a[href*='tag']")
    private var bestTags: List<Element>? = null

    @Selector(value = ".post-tag a[href*='label']")
    private var xxxToonsTags: List<Element>? = null

    @Selector(value = ".tagcloud a[href*='type']")
    private var allPornComixTags: List<Element>? = null


    @Selector(value = ".author-content a[href*='author']")
    private var galleryAuthorTags: List<Element>? = null

    @Selector(value = ".tags-content a[href*='tag']")
    private var galleryCommonTags: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.PORNCOMIX
        title = cleanup(title)
        if (title.isEmpty()) return Content(status = StatusContent.IGNORED)
        content.title = title
        content.url = url
        content.coverImageUrl = coverUrl

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
                } catch (e: JsonDataException) {
                    Timber.i(e)
                }
            }
        }

        return if (GALLERY_PATTERN.matcher(url).find())
            updateGallery(content, updateImages)
        else updateSingleChapter(content, updateImages)
    }

    private fun updateSingleChapter(content: Content, updateImages: Boolean): Content {
        var artist = ""
        if (content.url.contains("/manga")) {
            val titleParts = title.split("-")
            artist = titleParts[0].trim()
        }
        val attributes = AttributeMap()
        attributes.add(
            Attribute(AttributeType.ARTIST, artist, artist, Site.PORNCOMIX)
        )

        var res = tryProcessTags(mangaTags, attributes)
        if (!res) res = tryProcessTags(galleryTags, attributes)
        if (!res) res = tryProcessTags(zoneTags, attributes)
        if (!res) res = tryProcessTags(bestTags, attributes)
        if (!res) res = tryProcessTags(xxxToonsTags, attributes)
        if (!res) tryProcessTags(allPornComixTags, attributes)

        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }

    private fun updateGallery(content: Content, updateImages: Boolean): Content {
        val attributes = AttributeMap()

        parseAttributes(attributes, AttributeType.ARTIST, galleryAuthorTags, false, Site.PORNCOMIX)
        parseAttributes(attributes, AttributeType.TAG, galleryCommonTags, false, Site.PORNCOMIX)

        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }

    private fun tryProcessTags(elements: List<Element>?, attributes: AttributeMap): Boolean {
        elements?.let {
            if (it.isNotEmpty()) {
                parseAttributes(
                    attributes,
                    AttributeType.TAG,
                    mangaTags,
                    false,
                    Site.PORNCOMIX
                )
                return true
            }
        }
        return false
    }
}