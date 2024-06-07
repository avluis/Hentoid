package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.PUR_GALLERY_PATTERN
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.removeBrackets
import me.devsaki.hentoid.util.network.getHttpProtocol
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.regex.Pattern

private val GALLERY_PATTERN = Pattern.compile(PUR_GALLERY_PATTERN)

class PururinContent : BaseContentParser() {

    // SINGLE BOOK

    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var coverUrl: String

    @Selector(value = "div.title h1", defValue = "")
    private var title: List<String>? = null

    @Selector("table.table-info tr td")
    private var pages: List<String>? = null

    @Selector(value = "table.table-info a[href*='/tags/artist']")
    private var artists: List<Element>? = null

    @Selector(value = "table.table-info a[href*='/tags/circle']")
    private var circles: List<Element>? = null

    @Selector(value = "table.table-info a[href*='/tags/content']")
    private var tags: List<Element>? = null

    @Selector(value = "table.table-info a[href*='/tags/parody']")
    private var series: List<Element>? = null

    @Selector(value = "table.table-info a[href*='/tags/character']")
    private var characters: List<Element>? = null

    @Selector(value = "table.table-info a[href*='/tags/language']")
    private var languages: List<Element>? = null

    @Selector(value = "table.table-info a[href*='/tags/category']")
    private var categories: List<Element>? = null


    // COLLECTION

    @Selector(value = "span.title h1", defValue = "")
    private var collectionTitle: String? = null

    @Selector(value = "div.row-gallery a[href*='/gallery/'] img")
    private var collectionImgs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        var theUrl = url
        content.setSite(Site.PURURIN)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        if (url.endsWith("/")) theUrl = url.substring(0, url.length - 1)
        content.setUrl(theUrl)

        return if (GALLERY_PATTERN.matcher(url).find())
            updateGallery(content, updateImages)
        else updateSingleChapter(url, content, updateImages)
    }

    private fun updateSingleChapter(url: String, content: Content, updateImages: Boolean): Content {
        if (!coverUrl.startsWith("http")) coverUrl += getHttpProtocol(url) + ":" + coverUrl
        content.setCoverImageUrl(coverUrl)

        title?.let {
            content.setTitle(if (it.isNotEmpty()) cleanup(it[0]) else NO_TITLE)
        } ?: {
            content.setTitle(NO_TITLE)
        }

        if (updateImages) {
            var qtyPages = 0
            pages?.let { pgs ->
                var pagesFound = false
                for (s in pgs) {
                    if (pagesFound) {
                        qtyPages = removeBrackets(s).toInt()
                        break
                    }
                    if (s.trim().equals("pages", ignoreCase = true)) pagesFound = true
                }
            }
            content.setQtyPages(qtyPages)
            content.setImageFiles(emptyList())
        }
        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.PURURIN)
        parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.PURURIN)
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.PURURIN)
        parseAttributes(attributes, AttributeType.SERIE, series, false, Site.PURURIN)
        parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characters,
            false,
            Site.PURURIN
        )
        parseAttributes(
            attributes,
            AttributeType.LANGUAGE,
            languages,
            false,
            Site.PURURIN
        )
        parseAttributes(
            attributes,
            AttributeType.CATEGORY,
            categories,
            false,
            Site.PURURIN
        )
        content.putAttributes(attributes)
        return content
    }

    private fun updateGallery(content: Content, updateImages: Boolean): Content {
        collectionImgs?.let {
            if (it.isNotEmpty()) content.setCoverImageUrl(getImgSrc(it[0]))
        }

        collectionTitle?.let {
            content.setTitle(if (it.isNotEmpty()) cleanup(it) else NO_TITLE)
        } ?: {
            content.setTitle(NO_TITLE)
        }

        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        return content
    }
}