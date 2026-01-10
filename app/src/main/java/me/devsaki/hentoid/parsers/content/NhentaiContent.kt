package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.NhentaiActivity.Companion.FAVS_FILTER
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.images.NhentaiParser
import me.devsaki.hentoid.parsers.images.NhentaiParser.Companion.COVER_SELECTOR
import me.devsaki.hentoid.parsers.images.NhentaiParser.Companion.THUMBS_SELECTOR
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class NhentaiContent : BaseContentParser() {
    @Selector(value = "#bigcontainer #cover a", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = COVER_SELECTOR)
    private var cover: Element? = null

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private lateinit var title: String

    // Fallback value for title (see #449)
    @Selector(value = "#info h1", defValue = NO_TITLE)
    private lateinit var titleAlt: String

    @Selector(value = "#tags time", attr = "datetime", defValue = "")
    private lateinit var uploadDate: String

    @Selector(value = "#info a[href*='/artist']")
    private var artists: List<Element>? = null

    @Selector(value = "#info a[href^='/group/']")
    private var circles: List<Element>? = null

    @Selector(value = "#info a[href*='/tag']")
    private var tags: List<Element>? = null

    @Selector(value = "#info a[href*='/parody']")
    private var series: List<Element>? = null

    @Selector(value = "#info a[href*='/character']")
    private var characters: List<Element>? = null

    @Selector(value = "#info a[href*='/language']")
    private var languages: List<Element>? = null

    @Selector(value = "#info a[href*='/category']")
    private var categories: List<Element>? = null

    @Selector(value = THUMBS_SELECTOR)
    private var thumbs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.NHENTAI
        val theUrl = galleryUrl.ifEmpty { url }
        if (theUrl.isEmpty()) return Content(status = StatusContent.IGNORED)
        content.setRawUrl(url)

        return if (url.contains(FAVS_FILTER)) updateFavs(content, theUrl, updateImages)
        else updateGallery(content, theUrl, updateImages)
    }

    private fun updateFavs(content: Content, url: String, updateImages: Boolean): Content {
        content.title = "NHentai favourites"

        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }

        return content
    }

    private fun updateGallery(content: Content, url: String, updateImages: Boolean): Content {
        var isError = false
        thumbs?.apply { isError = isEmpty() } ?: run { isError = true }
        isError = isError || url.endsWith("favorite") // Fav button
        if (isError) return Content(status = StatusContent.IGNORED)

        cover?.let {
            content.coverImageUrl = fixUrl(getImgSrc(it), Site.NHENTAI.url)
        }
        var titleDef = title.trim()
        if (titleDef.isEmpty()) titleDef = titleAlt.trim()
        content.title = cleanup(titleDef)
        // e.g. 2022-03-20T00:09:43.309901+00:00, 2022-03-20T00:09:43+00:00
        content.uploadDate = parseDatetimeToEpoch(uploadDate, "yyyy-MM-dd'T'HH:mm:ss'.'nnnnnnXXX")
        if (0L == content.uploadDate) content.uploadDate = parseDatetimeToEpoch(uploadDate, "yyyy-MM-dd'T'HH:mm:ss'.'XXX")

        val attributes = AttributeMap()
        parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artists,
            false,
            "name",
            Site.NHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.CIRCLE,
            circles,
            false,
            "name",
            Site.NHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.TAG,
            tags,
            false,
            "name",
            Site.NHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.SERIE,
            series,
            false,
            "name",
            Site.NHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characters,
            false,
            "name",
            Site.NHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.LANGUAGE,
            languages,
            false,
            "name",
            Site.NHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.CATEGORY,
            categories,
            false,
            "name",
            Site.NHENTAI
        )
        content.putAttributes(attributes)
        if (updateImages) {
            thumbs?.let {
                val images = urlsToImageFiles(
                    NhentaiParser.parseImages(content.coverImageUrl, it),
                    content.coverImageUrl,
                    StatusContent.SAVED
                )
                content.setImageFiles(images)
                content.qtyPages = images.size - 1 // Don't count the cover
            }
        }
        return content
    }
}