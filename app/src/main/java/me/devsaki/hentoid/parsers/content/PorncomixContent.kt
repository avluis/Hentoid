package me.devsaki.hentoid.parsers.content

import com.squareup.moshi.JsonDataException
import me.devsaki.hentoid.database.domains.Attribute
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


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.PORNCOMIX)
        title = title.trim { it <= ' ' }
        if (title.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setTitle(StringHelper.removeNonPrintableChars(title.trim { it <= ' ' }))
        content.setUrl(url)
        content.setCoverImageUrl(coverUrl)
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
                } catch (e: JsonDataException) {
                    Timber.i(e)
                }
            }
        }
        var artist = ""
        if (content.url.contains("/manga")) {
            val titleParts = title.split("-")
            artist = titleParts[0].trim { it <= ' ' }
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
            content.setQtyPages(0)
        }
        return content
    }

    private fun tryProcessTags(elements: List<Element>?, attributes: AttributeMap): Boolean {
        elements?.let {
            if (it.isNotEmpty()) {
                ParseHelper.parseAttributes(
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