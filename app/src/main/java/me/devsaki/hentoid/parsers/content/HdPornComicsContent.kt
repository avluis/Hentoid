package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.parsers.images.HdPornComicsParser
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class HdPornComicsContent : BaseContentParser() {
    @Selector(value = "h1", defValue = NO_TITLE)
    private lateinit var title: String

    @Selector(
        value = "head meta[property=\"article:published_time\"]",
        attr = "content",
        defValue = ""
    )
    private lateinit var uploadDate: String

    @Selector(value = "head link[rel='shortlink']", attr = "href", defValue = "")
    private lateinit var shortlink: String

    @Selector(value = "#imgBox img")
    private var cover: Element? = null

    @Selector(value = "#infoBox a[href*='/artist/']")
    private var artists: List<Element>? = null

    @Selector(value = "#infoBox a[href*='/tag/']")
    private var tags: List<Element>? = null

    @Selector(value = "figure a picture img")
    private var pages: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.HDPORNCOMICS)
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        content.setTitle(StringHelper.removeNonPrintableChars(title))
        if (shortlink.isNotEmpty()) {
            val equalIndex = shortlink.lastIndexOf('=')
            if (equalIndex > -1) content.uniqueSiteId = shortlink.substring(equalIndex + 1)
        }
        if (uploadDate.isNotEmpty()) content.setUploadDate(
            Helper.parseDatetimeToEpoch(
                uploadDate,
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            )
        ) // e.g. 2021-08-08T20:53:49+00:00
        var coverUrl: String? = ""
        cover?.let {
            coverUrl = ParseHelper.getImgSrc(it)
            content.setCoverImageUrl(coverUrl)
        }
        val attributes = AttributeMap()
        ParseHelper.parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artists,
            false,
            Site.HDPORNCOMICS
        )
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.HDPORNCOMICS)
        content.putAttributes(attributes)
        if (updateImages) {
            pages?.let {
                val imgs = HdPornComicsParser.parseImages(it)
                content.setImageFiles(
                    ParseHelper.urlsToImageFiles(
                        imgs,
                        coverUrl!!,
                        StatusContent.SAVED
                    )
                )
                content.setQtyPages(imgs.size - 1) // Don't count the cover
            }
        }
        return content
    }
}