package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.parseDateToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

/**
 * Parser for Simply Hentai HTML content
 */
class SimplyContent : BaseContentParser() {
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var coverUrl: String

    @Selector(value = ".album-info h1", defValue = "")
    private lateinit var title: String

    @Selector(value = ".album-info .col-5 div")
    private var ulDateContainer: Element? = null

    @Selector(value = ".album-info a[href*='/language/']")
    private var languageTags: List<Element>? = null

    @Selector(value = ".album-info a[href*='/character/']")
    private var characterTags: List<Element>? = null

    @Selector(value = ".album-info a[href*='/series/']")
    private var seriesTags: List<Element>? = null

    @Selector(value = ".album-info a[href*='/artist/']")
    private var artistsTags: List<Element>? = null

    @Selector(value = ".album-info a[href*='/tag/']")
    private var tags: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.SIMPLY)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setCoverImageUrl(coverUrl)
        content.setRawUrl(url)
        if (title.isNotEmpty()) {
            content.setTitle(cleanup(title))
        } else content.setTitle(NO_TITLE)

        ulDateContainer?.let {
            content.setUploadDate(
                parseDateToEpoch(it.ownText(), "M/d/yyyy")
            ) // e.g. 10/23/2022, 12/8/2022
        }

        val attributes = AttributeMap()
        parseAttributes(
            attributes,
            AttributeType.LANGUAGE,
            languageTags,
            false,
            Site.SIMPLY
        )
        parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characterTags,
            false,
            Site.SIMPLY
        )
        parseAttributes(attributes, AttributeType.SERIE, seriesTags, false, Site.SIMPLY)
        parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artistsTags,
            false,
            Site.SIMPLY
        )
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.SIMPLY)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        return content
    }
}