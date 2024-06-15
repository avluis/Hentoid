package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class MrmContent : BaseContentParser() {
    @Selector(value = "article h1", defValue = "")
    private lateinit var title: String

    @Selector(value = "time.entry-time", attr = "datetime", defValue = "")
    private lateinit var uploadDate: String

    @Selector(".entry-header .entry-meta .entry-categories a")
    private var categories: List<Element>? = null

    @Selector(value = ".entry-header .entry-terms a[href*='/lang/']")
    private var languages: List<Element>? = null

    @Selector(value = ".entry-header .entry-terms a[href*='/genre/']")
    private var genres: List<Element>? = null

    @Selector(value = ".entry-header .entry-tags a[href*='/tag/']")
    private var tags: List<Element>? = null

    @Selector(value = ".entry-content img")
    private var images: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.MRM)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        content.setTitle(cleanup(title))

        if (uploadDate.isNotEmpty())
            content.setUploadDate(
                parseDatetimeToEpoch(uploadDate, "yyyy-MM-dd'T'HH:mm:ssXXX")
            ) // e.g. 2022-03-20T00:09:43+07:00

        images?.let {
            if (it.isNotEmpty())
                content.setCoverImageUrl(getImgSrc(it[0]))
        }

        val attributes = AttributeMap()
        // On MRM, most titles are formatted "[Artist] Title" although there's no actual artist field on the book page
        if (title.startsWith("[")) {
            val closingBracketIndex = title.indexOf(']')
            if (closingBracketIndex > -1) {
                val attribute = Attribute(
                    AttributeType.ARTIST,
                    title.substring(1, closingBracketIndex),
                    "",
                    Site.MRM
                )
                attributes.add(attribute)
            }
        }
        parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.MRM)
        parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.MRM)
        parseAttributes(attributes, AttributeType.TAG, genres, false, Site.MRM)
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MRM)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        return content
    }
}