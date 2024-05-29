package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.images.HentaifoxParser
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.Locale

class HentaifoxContent : BaseContentParser() {
    @Selector(value = ".cover img")
    private var cover: Element? = null

    @Selector(value = ".info h1", defValue = "")
    private lateinit var title: String

    @Selector(".info")
    private var information: Element? = null

    @Selector(value = ".g_thumb img")
    private var thumbs: List<Element>? = null

    @Selector(value = "body script")
    private var scripts: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.HENTAIFOX)
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        content.populateUniqueSiteId()
        cover?.let {
            content.setCoverImageUrl(getImgSrc(it))
        }
        content.setTitle(cleanup(title))

        information?.let { info ->
            if (info.children().isEmpty()) return content

            var qtyPages = 0
            val attributes = AttributeMap()
            for (e in info.children()) {
                // Flat info (pages, posted date)
                if (e.children().isEmpty() && e.hasText()) {
                    if (e.text().lowercase(Locale.getDefault()).startsWith("pages")) {
                        qtyPages = e.text().lowercase(Locale.getDefault()).replace(" ", "")
                            .replace("pages:", "").toInt()
                    }
                } else if (e.children().size > 1) { // Tags
                    val metaType = e.child(0).text().replace(":", "").trim { it <= ' ' }
                    val tagLinks: List<Element> = e.select("a")
                    if (metaType.equals("artists", ignoreCase = true)) parseAttributes(
                        attributes,
                        AttributeType.ARTIST,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                    if (metaType.equals("parodies", ignoreCase = true)) parseAttributes(
                        attributes,
                        AttributeType.SERIE,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                    if (metaType.equals(
                            "characters",
                            ignoreCase = true
                        )
                    ) parseAttributes(
                        attributes,
                        AttributeType.CHARACTER,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                    if (metaType.equals("tags", ignoreCase = true)) parseAttributes(
                        attributes,
                        AttributeType.TAG,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                    if (metaType.equals("groups", ignoreCase = true)) parseAttributes(
                        attributes,
                        AttributeType.CIRCLE,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                    if (metaType.equals(
                            "languages",
                            ignoreCase = true
                        )
                    ) parseAttributes(
                        attributes,
                        AttributeType.LANGUAGE,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                    if (metaType.equals("category", ignoreCase = true)) parseAttributes(
                        attributes,
                        AttributeType.CATEGORY,
                        tagLinks,
                        true,
                        Site.HENTAIFOX
                    )
                }
            }
            content.putAttributes(attributes)

            if (updateImages) {
                content.setQtyPages(qtyPages)
                thumbs?.let { th ->
                    scripts?.let { scr ->
                        content.setImageFiles(
                            urlsToImageFiles(
                                HentaifoxParser.parseImages(content, th, scr),
                                content.coverImageUrl, StatusContent.SAVED
                            )
                        )
                    }
                }
            }
        }
        return content
    }
}