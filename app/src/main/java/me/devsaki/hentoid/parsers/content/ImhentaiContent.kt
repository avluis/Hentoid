package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.removeTextualTags
import me.devsaki.hentoid.util.StringHelper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class ImhentaiContent : BaseContentParser() {
    @Selector(value = "div.left_cover img")
    private var cover: Element? = null

    @Selector(value = "div.right_details h1", defValue = "")
    private lateinit var title: String

    @Selector(value = "li.pages", defValue = "")
    private lateinit var pages: String

    @Selector(value = "ul.galleries_info a[href*='/artist']")
    private var artists: List<Element>? = null

    @Selector(value = "ul.galleries_info a[href*='/group']")
    private var circles: List<Element>? = null

    @Selector(value = "ul.galleries_info a[href*='/tag']")
    private var tags: List<Element>? = null

    @Selector(value = "ul.galleries_info a[href*='/language']")
    private var languages: List<Element>? = null

    @Selector(value = "ul.galleries_info a[href*='/category']")
    private var categories: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.IMHENTAI)
        content.setRawUrl(url)
        cover?.let {
            content.setCoverImageUrl(getImgSrc(it))
        }
        var str = if (title.isNotEmpty()) StringHelper.removeNonPrintableChars(title) else ""
        str = removeTextualTags(str)
        content.setTitle(str)
        if (updateImages) {
            var qtyPages = 0
            if (pages.isNotEmpty()) {
                str = pages.replace("Pages", "").replace("pages", "").replace(":", "")
                    .trim { it <= ' ' }
                qtyPages = str.toInt()
            }
            content.setImageFiles(emptyList())
            content.setQtyPages(qtyPages)
        }
        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.IMHENTAI)
        parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.IMHENTAI)
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.IMHENTAI)
        parseAttributes(
            attributes,
            AttributeType.LANGUAGE,
            languages,
            false,
            Site.IMHENTAI
        )
        parseAttributes(
            attributes,
            AttributeType.CATEGORY,
            categories,
            false,
            Site.IMHENTAI
        )
        content.putAttributes(attributes)
        return content
    }
}