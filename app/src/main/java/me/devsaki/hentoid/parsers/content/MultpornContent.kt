package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.parsers.images.MultpornParser
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException

class MultpornContent : BaseContentParser() {
    @Selector(value = "head link[rel=shortlink]", attr = "href", defValue = "")
    private lateinit var shortlink: String

    @Selector(value = "#page-title", defValue = "")
    private lateinit var title: String

    @Selector(value = "head meta[name=dcterms.date]", attr = "content", defValue = "")
    private lateinit var publishingDate: String

    @Selector(value = "head script")
    private var headScripts: List<Element>? = null

    @Selector(value = ".links a[href^='/characters']")
    private var characterTags: List<Element>? = null

    @Selector(value = ".links a[href^='/hentai']")
    private var seriesTags1: List<Element>? = null

    @Selector(value = ".links a[href^='/comics']")
    private var seriesTags2: List<Element>? = null

    @Selector(value = ".links a[href^='/authors']")
    private var artistsTags: List<Element>? = null

    @Selector(value = ".links a[href^='/category']")
    private var tags: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.setSite(Site.MULTPORN)
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        if (title.isNotEmpty()) {
            content.setTitle(StringHelper.removeNonPrintableChars(title))
        } else content.setTitle(NO_TITLE)
        val shortlinkParts = shortlink.split("/")
        content.uniqueSiteId = shortlinkParts[shortlinkParts.size - 1]
        if (publishingDate.isNotEmpty()) // e.g. 2018-11-12T20:04-05:00
            content.setUploadDate(
                Helper.parseDatetimeToEpoch(publishingDate, "yyyy-MM-dd'T'HH:mmXXX")
            )
        val attributes = AttributeMap()
        ParseHelper.parseAttributes(
            attributes,
            AttributeType.CHARACTER,
            characterTags,
            false,
            Site.MULTPORN
        )
        ParseHelper.parseAttributes(
            attributes,
            AttributeType.SERIE,
            seriesTags1,
            false,
            Site.MULTPORN
        )
        ParseHelper.parseAttributes(
            attributes,
            AttributeType.SERIE,
            seriesTags2,
            false,
            Site.MULTPORN
        )
        ParseHelper.parseAttributes(
            attributes,
            AttributeType.ARTIST,
            artistsTags,
            false,
            Site.MULTPORN
        )
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MULTPORN)
        content.putAttributes(attributes)
        val juiceboxRequestUrl = MultpornParser.getJuiceboxRequestUrl(headScripts!!)
        try {
            val imagesUrls = MultpornParser.getImagesUrls(juiceboxRequestUrl, url)
            if (imagesUrls.isNotEmpty()) {
                content.setCoverImageUrl(imagesUrls[0])
                if (updateImages) {
                    content.setImageFiles(
                        ParseHelper.urlsToImageFiles(
                            imagesUrls,
                            imagesUrls[0], StatusContent.SAVED
                        )
                    )
                    content.setQtyPages(imagesUrls.size)
                }
            }
        } catch (e: IOException) {
            Timber.w(e)
            return Content().setStatus(StatusContent.IGNORED)
        }
        return content
    }
}