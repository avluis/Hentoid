package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.YoastGalleryMetadata
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.parseAttributes
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.parseDatetimeToEpoch
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException

class EromangaContent : BaseContentParser() {

    @Selector(value = "head meta[name=description]", attr = "content", defValue = "")
    private lateinit var title: String

    @Selector(value = ".single_thumbs img")
    private var thumb: Element? = null

    @Selector(value = "head script.aioseop-schema")
    private var metadata: Element? = null

    @Selector(value = ".single-content a[href*='/tag/']")
    private var tags1: List<Element>? = null

    @Selector(value = ".single-content a[href*='/category/']")
    private var tags2: List<Element>? = null

    @Selector(value = ".entry-content img")
    private var images: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.EROMANGA
        if (url.isEmpty()) return Content(status = StatusContent.IGNORED)
        content.setRawUrl(url)
        thumb?.let {
            content.coverImageUrl = getImgSrc(it)
        }
        content.title = cleanup(title)

        // Publish date
        metadata?.apply {
            if (childNodeSize() > 0) {
                try {
                    jsonToObject(
                        childNode(0).toString(),
                        YoastGalleryMetadata::class.java
                    )?.let { galleryMeta ->
                        val publishDate =
                            galleryMeta.getDatePublished() // e.g. 2026-02-11T11:00:58+09:00
                        if (publishDate.isNotEmpty()) content.uploadDate =
                            parseDatetimeToEpoch(publishDate, "yyyy-MM-dd'T'HH:mm:ssXXX")
                    }
                } catch (e: IOException) {
                    Timber.i(e)
                }
            }
        }

        val allTags = HashSet<Element>()
        tags1?.let { allTags.addAll(it) }
        tags2?.let { allTags.addAll(it) }
        allTags.distinctBy { it.attr("href") }

        val attributes = AttributeMap()
        parseAttributes(
            attributes,
            AttributeType.TAG,
            allTags,
            false,
            Site.EROMANGA
        )

        content.putAttributes(attributes)
        if (updateImages) {
            images?.let { imgs ->
                val imgUrls = imgs.map { getImgSrc(it) }
                content.setImageFiles(
                    urlsToImageFiles(
                        imgUrls, content.coverImageUrl,
                        StatusContent.SAVED
                    )
                )
            } ?: run {
                content.setImageFiles(emptyList())
            }
            content.qtyPages = content.imageList.count { it.isReadable }
        }
        return content
    }
}