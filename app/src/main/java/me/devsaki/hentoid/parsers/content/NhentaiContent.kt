package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.images.NhentaiParser
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class NhentaiContent : BaseContentParser() {
    @Selector(value = "#bigcontainer #cover a", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = "#cover img")
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

    @Selector(value = "#thumbnail-container img[data-src]")
    private var thumbs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.NHENTAI
        val theUrl = galleryUrl.ifEmpty { url }

        var isError = false
        isError = isError || theUrl.isEmpty()
        thumbs?.let {
            isError = isError || it.isEmpty()
        } ?: {
            isError = true
        }
        isError = isError || theUrl.endsWith("favorite") // Fav button
        if (isError) return Content(status = StatusContent.IGNORED)


        content.setRawUrl(theUrl)
        cover?.let {
            content.coverImageUrl = getImgSrc(it)
        }
        var titleDef = title.trim()
        if (titleDef.isEmpty()) titleDef = titleAlt.trim()
        content.title = cleanup(titleDef)
        // e.g. 2022-03-20T00:09:43.309901+00:00
        content.uploadDate = parseDatetimeToEpoch(uploadDate, "yyyy-MM-dd'T'HH:mm:ss'.'nnnnnnXXX")

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
                    NhentaiParser.parseImages(content, it),
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