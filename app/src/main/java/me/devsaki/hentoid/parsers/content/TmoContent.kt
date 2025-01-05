package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class TmoContent : BaseContentParser() {
    @Selector(value = "#bigcontainer #cover a", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = "img.content-thumbnail-cover[title='cover']")
    private var cover: Element? = null

    @Selector(value = ".panel-title", defValue = "")
    private lateinit var title: String

    @Selector(value = ".tag a[href*='[searchBy]=artist']")
    private var artists: List<Element>? = null

    @Selector(value = "a.tag[href*='?genders']")
    private var tags: List<Element>? = null

    @Selector(value = "a.tag[href*='[searchBy]=tag']")
    private var tags2: List<Element>? = null

    @Selector(value = ".content-property .flag-icon")
    private var languages: List<Element>? = null

    @Selector(value = ".panel-body a.lanzador", attr = "href", defValue = "")
    private var thumbs: List<String>? = null


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
        if (titleDef.isEmpty()) titleDef = NO_TITLE
        content.title = cleanup(titleDef)

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

        languages?.firstOrNull()?.let {
            if (it.attr("class").contains("flag-icon-es")) {
                attributes.add(Attribute(AttributeType.LANGUAGE, "Español", "", Site.TMO))
            }
        }

        content.putAttributes(attributes)
        if (updateImages) {
            thumbs?.let { t ->
                val galleryPages = t.filterNot { it.isEmpty() }
                    .mapIndexed { i, s ->
                        ImageFile.fromPageUrl(
                            i + 1,
                            s,
                            StatusContent.SAVED,
                            t.size
                        )
                    }
                content.setImageFiles(galleryPages)
                content.qtyPages = galleryPages.size
            } ?: run {
                content.setImageFiles(emptyList())
                content.qtyPages = 0
            }
        }
        return content
    }
}