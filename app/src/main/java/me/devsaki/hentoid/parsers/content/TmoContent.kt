package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.images.TmoParser
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class TmoContent : BaseContentParser() {
    @Selector(value = "#bigcontainer #cover a", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = "img.content-thumbnail-cover[title='cover']")
    private var cover: Element? = null

    @Selector(value = ".panel-title", defValue = "")
    private lateinit var title: String

    // Fallback value for title (see #449)
    @Selector(value = "#info h1", defValue = NO_TITLE)
    private lateinit var titleAlt: String

    @Selector(value = "#tags time", attr = "datetime", defValue = "")
    private lateinit var uploadDate: String

    @Selector(value = ".tag a[href*='[searchBy]=artist']")
    private var artists: List<Element>? = null

    @Selector(value = "a.tag[href*='?genders']")
    private var tags: List<Element>? = null

    @Selector(value = "a.tag[href*='[searchBy]=tag']")
    private var tags2: List<Element>? = null

    @Selector(value = "#info a[href*='/language']")
    private var languages: List<Element>? = null

    @Selector(value = ".panel-body img[data-toggle]")
    private var thumbs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.TMO
        val theUrl = galleryUrl.ifEmpty { url }

        var isError = false
        isError = isError || theUrl.isEmpty()
        thumbs?.let {
            isError = isError || it.isEmpty()
        } ?: run {
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
            Site.TMO
        )
        parseAttributes(
            attributes,
            AttributeType.TAG,
            tags,
            false,
            Site.TMO
        )
        parseAttributes(
            attributes,
            AttributeType.TAG,
            tags2,
            false,
            Site.TMO
        )
        parseAttributes(
            attributes,
            AttributeType.LANGUAGE,
            languages,
            false,
            Site.TMO
        )
        content.putAttributes(attributes)
        if (updateImages) {
            thumbs?.let {
                val images = urlsToImageFiles(
                    TmoParser.parseImages(content, it),
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