package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.images.DoujinsParser
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.parseDateToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import java.util.Locale

class DoujinsContent : BaseContentParser() {
    @Selector(value = ".folder-title a")
    private var breadcrumbs: List<Element>? = null

    @Selector("img.doujin")
    private var images: List<Element>? = null

    @Selector(value = "a[href*='/artists/']")
    private var artists: List<Element>? = null

    @Selector(value = "a[href*='/searches?tag_id=']") // To deduplicate
    private var tags: List<Element>? = null

    @Selector(value = "#content .folder-message")
    private var contentInfo: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.DOUJINS
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        breadcrumbs?.let {
            if (it.isNotEmpty()) {
                val e = it[it.size - 1]
                content.title = cleanup(e.text())
            }
        }
        contentInfo?.let { ci ->
            if (ci.isNotEmpty()) {
                for (e in ci) {
                    val txt = e.text().lowercase(Locale.getDefault())
                    if (txt.contains("•") && !txt.contains("translated")) { // e.g. March 16th, 2022 • 25 images
                        val parts = e.text().split("•")
                        content.uploadDate = parseDateToEpoch(parts[0], "MMMM d',' yyyy")
                        break
                    }
                }
            }
        }
        if (images != null) {
            images?.let {
                if (it.isNotEmpty()) {
                    // Cover = thumb from the 1st page
                    val coverUrl = it[0].attr("data-thumb2")
                    content.coverImageUrl = coverUrl
                    if (updateImages) {
                        val imageUrls = DoujinsParser.parseImages(it)
                        content.qtyPages = imageUrls.size - 1 // Don't count the cover
                        content.setImageFiles(
                            urlsToImageFiles(
                                imageUrls,
                                content.coverImageUrl,
                                StatusContent.SAVED
                            )
                        )
                    }
                }
            }
        } else if (updateImages) {
            content.qtyPages = 0
            content.setImageFiles(emptyList())
        }

        // Deduplicate tags
        val attributes = AttributeMap()
        parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artists,
            false,
            Site.DOUJINS
        )
        parseAttributes(
            attributes,
            AttributeType.TAG,
            tags,
            false,
            Site.DOUJINS
        )
        content.putAttributes(attributes)
        return content
    }
}