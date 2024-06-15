package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.keepDigits
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.Locale

class AnchiraContent : BaseContentParser() {

    @Selector(value = "#metadata h2", defValue = NO_TITLE)
    private lateinit var title: String

    @Selector(value = "#metadata span")
    private var extraData: List<Element>? = null

    @Selector(value = "#metadata a[href*='/?s=artist:']")
    private var artists: List<Element>? = null

    @Selector(value = "#metadata a[href*='/?s=parody:']")
    private var parodies: List<Element>? = null

    @Selector(value = "#metadata a[href*='/?s=tag:']")
    private var tags: List<Element>? = null

    @Selector(value = "#gallery img")
    private var imgs: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        content.site = Site.ANCHIRA
        content.setRawUrl(url)
        if (null == imgs) return content.setStatus(StatusContent.IGNORED)
        content.title = cleanup(title)
        imgs?.let {
            if (it.isNotEmpty()) content.coverImageUrl = getImgSrc(it[0])
        }
        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.ANCHIRA)
        parseAttributes(attributes, AttributeType.SERIE, parodies, true, Site.ANCHIRA)
        parseAttributes(attributes, AttributeType.TAG, tags, true, Site.ANCHIRA)
        content.putAttributes(attributes)
        var nbPages = 0
        extraData?.let {
            for (e in it) {
                var txt = e.text().lowercase(Locale.getDefault())
                if (txt.contains("page")) {
                    txt = keepDigits(txt)
                    if (isNumeric(txt)) {
                        nbPages = txt.toInt()
                        break
                    }
                }
            }
        }
        content.qtyPages = nbPages
        if (updateImages) content.setImageFiles(emptyList())

        return content
    }
}