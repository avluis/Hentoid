package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.parsers.images.DeviantArtParser
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector

class DeviantArtContent : BaseContentParser() {
    @Selector(value = "body")
    private lateinit var body: Element

    @Selector(value = "meta[property='og:title']", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = "time", attr = "datetime", defValue = "") // Gets the first element
    private lateinit var uploadDate: String

    @Selector(value = "a[href*='/tag/']")
    private var tags: List<Element>? = null

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.DEVIANTART)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        if (title.isNotEmpty()) {
            title = StringHelper.removeNonPrintableChars(title.trim())
        } else content.setTitle(NO_TITLE)

        if (uploadDate.isNotEmpty())
            content.setUploadDate(
                Helper.parseDatetimeToEpoch(uploadDate, "yyyy-MM-dd'T'HH:mm:ss.SSSX")
            ) // e.g. 2022-03-20T00:09:43.000Z

        val attributes = AttributeMap()
        // On DeviantArt, most titles are formatted "Title by Artist on DeviantArt"
        var index2 = title.lastIndexOf(" on DeviantArt", ignoreCase = true)
        if (-1 == index2) index2 = title.lastIndex
        var index1 = title.lastIndexOf(" by ", index2, true)
        if (-1 == index1) index1 = index2
        content.setTitle(title.substring(0, index1))

        if (index1 < index2) {
            val attribute = Attribute(
                AttributeType.ARTIST,
                title.substring(index1 + 4, index2),
                "",
                Site.DEVIANTART
            )
            attributes.add(attribute)
        }

        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.DEVIANTART)
        content.putAttributes(attributes)

        val imgs = DeviantArtParser.parseDeviation(body)
        if (imgs.first.isNotEmpty()) content.setCoverImageUrl(imgs.first)

        if (updateImages) {
            val img = ImageFile.fromPageUrl(1, url, StatusContent.SAVED, 1)
            content.setImageFiles(listOf(img))
            content.setQtyPages(1)
        }
        return content
    }
}