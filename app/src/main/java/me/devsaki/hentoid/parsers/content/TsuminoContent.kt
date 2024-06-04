package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.Helper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class TsuminoContent : BaseContentParser() {
    @Selector(value = "div.book-page-cover a", attr = "href", defValue = "")
    private lateinit var galleryUrl: String

    @Selector(value = "img.book-page-image")
    private var cover: Element? = null

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = "div#Uploaded", defValue = "")
    private lateinit var uploadDate: String

    @Selector(value = "div#Pages", defValue = "")
    private lateinit var pages: String

    @Selector(value = "div#Artist a")
    private var artists: List<Element>? = null

    @Selector(value = "div#Group a")
    private var circles: List<Element>? = null

    @Selector(value = "div#Tag a")
    private var tags: List<Element>? = null

    @Selector(value = "div#Parody a")
    private var series: List<Element>? = null

    @Selector(value = "div#Character a")
    private var characters: List<Element>? = null

    @Selector(value = "div#Category a")
    private var categories: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.TSUMINO)
        val theUrl = galleryUrl.ifEmpty { url }
        if (theUrl.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(theUrl)

        var coverUrl = ""
        cover?.let {
            coverUrl = getImgSrc(it)
        }
        if (!coverUrl.startsWith("http")) coverUrl = Site.TSUMINO.url + coverUrl
        content.setCoverImageUrl(coverUrl)
        content.setTitle(cleanup(title))
        content.setUploadDate(
            Helper.parseDateToEpoch(uploadDate, "yyyy MMMM dd")
        ) // e.g. 2021 December 13
        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.TSUMINO)
        parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.TSUMINO)
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.TSUMINO)
        parseAttributes(attributes, AttributeType.SERIE, series, false, Site.TSUMINO)
        parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characters,
            false,
            Site.TSUMINO
        )
        parseAttributes(
            attributes,
            AttributeType.CATEGORY,
            categories,
            false,
            Site.TSUMINO
        )
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(if (pages.isNotEmpty()) pages.toInt() else 0)
        }
        return content
    }
}