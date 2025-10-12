package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.completedStr
import me.devsaki.hentoid.util.network.getHttpProtocol
import me.devsaki.hentoid.util.ongoingStr
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class MangagoContent : BaseContentParser() {

    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    // TODO also see <script> that contains manga_name
    private lateinit var title: String

    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private lateinit var coverUrl: String

    @Selector(value = "#information a[href*='l_search/?name=']")
    private var authors: List<Element>? = null

    @Selector(value = "#information a[href*='/genre/']")
    private var tags: List<Element>? = null

    @Selector(value = ".uk-list span")
    private var status: List<Element>? = null

    @Selector(value = "title", defValue = "")
    private lateinit var chapterTitle1: String

    @Selector(value = "#series", defValue = "")
    private lateinit var chapterTitle2: String


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.MANGAGO
        content.setRawUrl(url)
        content.title = cleanup(title)

        if (content.title.isEmpty())
            content.title = cleanup(
                chapterTitle1
                    .replace(" - Mangago", "")
                    .replace(" Page 1", "")
            )
        if (content.title.isEmpty()) content.title = cleanup(chapterTitle2)

        if (coverUrl.isNotEmpty()) {
            if (!coverUrl.startsWith("http")) coverUrl += getHttpProtocol(url) + ":" + coverUrl
            content.coverImageUrl = coverUrl
        }

        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MANGAGO)
        parseAttributes(attributes, AttributeType.ARTIST, authors, false, Site.MANGAGO)

        // Ongoing / Completed
        status?.forEach {
            if (it.ownText().contains("ongoing", true) || it.ownText().contains("on going", true)) {
                attributes.add(
                    Attribute(
                        AttributeType.TAG, ongoingStr, "", Site.MANGAGO
                    )
                )
            }
            if (it.ownText().contains("completed", true)) {
                attributes.add(
                    Attribute(
                        AttributeType.TAG, completedStr, "", Site.MANGAGO
                    )
                )
            }
        }

        content.putAttributes(attributes)

        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }

        return content
    }
}