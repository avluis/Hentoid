package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.activities.sources.EdoujinActivity
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.images.EdoujinParser
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.Helper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.regex.Pattern

class EdoujinContent : BaseContentParser() {
    companion object {
        private val GALLERY_PATTERN = Pattern.compile(EdoujinActivity.GALLERY_PATTERN)
    }

    @Selector(value = ".thumb img")
    private var cover: Element? = null

    @Selector(value = ".entry-title")
    private var title: Element? = null

    @Selector(".infox .fmed")
    private var artist: List<Element>? = null

    @Selector(".mgen a")
    private var properties: List<Element>? = null

    @Selector(value = "time[itemprop='datePublished']", attr = "datetime")
    private var datePosted: String? = null

    @Selector(value = "time[itemprop='dateModified']", attr = "datetime")
    private var dateModified: String? = null

    @Selector(value = "script")
    private var scripts: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.EDOUJIN
        if (url.isEmpty()) return Content().setStatus(StatusContent.IGNORED)
        content.setRawUrl(url)
        return if (GALLERY_PATTERN.matcher(url).find())
            updateGallery(content, url, updateImages)
        else updateSingleChapter(content, url, updateImages)
    }

    private fun updateSingleChapter(
        content: Content,
        url: String,
        updateImages: Boolean
    ): Content {
        var urlParts = url.split("/")
        if (urlParts.size > 1) {
            urlParts = urlParts[urlParts.size - 1].split("-")
            if (urlParts.size > 1) content.uniqueSiteId = urlParts[urlParts.size - 1]
        }
        content.title = cleanup(title?.text())
        try {
            val info = EdoujinParser.getDataFromScripts(scripts)
            if (info != null) {
                val chapterImgs = info.getImages()
                if (updateImages && chapterImgs.isNotEmpty()) {
                    val coverUrl = chapterImgs[0]
                    content.setImageFiles(
                        urlsToImageFiles(
                            chapterImgs,
                            coverUrl,
                            StatusContent.SAVED
                        )
                    )
                    content.qtyPages = chapterImgs.size
                }
            }
        } catch (ioe: IOException) {
            Timber.w(ioe)
        }
        return content
    }

    private fun updateGallery(content: Content, url: String, updateImages: Boolean): Content {
        cover?.let {
            content.coverImageUrl = getImgSrc(it)
        }
        content.title = cleanup(title?.text())

        val urlParts = url.split("/")
        if (urlParts.size > 1) content.uniqueSiteId = urlParts[urlParts.size - 1]

        content.uploadDate = -1
        dateModified?.let {
            if (it.isNotEmpty())
                content.uploadDate = Helper.parseDatetimeToEpoch(
                    it,
                    "yyyy-MM-dd'T'HH:mm:ssXXX"
                ) // e.g. 2022-02-02T02:44:17+07:00
        }

        if (-1L == content.uploadDate) {
            datePosted?.let {
                if (it.isNotEmpty()) content.uploadDate = Helper.parseDatetimeToEpoch(
                    it, "yyyy-MM-dd'T'HH:mm:ssXXX"
                ) // e.g. 2022-02-02T02:44:17+07:00
            }
        }

        val attributes = AttributeMap()
        parseAttributes(attributes, AttributeType.TAG, properties, false, Site.EDOUJIN)
        var currentProperty = ""

        artist?.let { art ->
            art.forEach { e ->
                for (child in e.children()) {
                    if (child.nodeName() == "b") currentProperty =
                        child.text().lowercase(Locale.getDefault())
                            .trim { it <= ' ' } else if (child.nodeName() == "span") {
                        when (currentProperty) {
                            "artist", "author" -> {
                                val data =
                                    cleanup(
                                        child.text().lowercase(
                                            Locale.getDefault()
                                        ).trim()
                                    )
                                if (data.length > 1) attributes.add(
                                    Attribute(
                                        AttributeType.ARTIST,
                                        data,
                                        "",
                                        Site.EDOUJIN
                                    )
                                )
                            }

                            else -> {}
                        }
                    }
                }
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