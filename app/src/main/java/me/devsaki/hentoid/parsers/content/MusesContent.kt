package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber
import java.io.IOException
import java.util.Locale

class MusesContent : BaseContentParser() {
    @Selector(value = ".top-menu-breadcrumb a")
    private var breadcrumbs: List<Element>? = null

    @Selector(value = ".gallery a")
    private var thumbLinks: List<Element>? = null

    companion object {
        private val nonLegitPublishers: MutableList<String> = ArrayList()
        private val publishersWithAuthors: MutableList<String> = ArrayList()

        init {
            nonLegitPublishers.add("various authors")
            nonLegitPublishers.add("hentai and manga english")

            publishersWithAuthors.add("various authors")
            publishersWithAuthors.add("fakku comics")
            publishersWithAuthors.add("hentai and manga english")
            publishersWithAuthors.add("renderotica comics")
            publishersWithAuthors.add("tg comics")
            publishersWithAuthors.add("affect3d comics")
            publishersWithAuthors.add("johnpersons.com comics")
        }
    }

    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        // Gallery pages are the only ones whose gallery links end with numbers
        // The others are album lists
        var nbImages = 0
        val imagesUrls: MutableList<String> = ArrayList()

        thumbLinks?.let {
            for (thumbLink in it) {
                val href = thumbLink.attr("href")
                val numSeparator = href.lastIndexOf('/')
                if (StringHelper.isNumeric(href.substring(numSeparator + 1))) {
                    val img = thumbLink.select("img").first() ?: continue
                    val src = ParseHelper.getImgSrc(img)
                    if (src.isEmpty()) continue
                    imagesUrls.add(src)
                    nbImages++
                }
            }
            if (nbImages < it.size / 3) return Content().setStatus(StatusContent.IGNORED)
        }
        content.setSite(Site.MUSES)
        val theUrl = canonicalUrl.ifEmpty { url }
        if (theUrl.isEmpty() || 0 == nbImages) return content.setStatus(StatusContent.IGNORED)
        content.setRawUrl(theUrl)

        // == Circle (publisher), Artist and Series
        val attributes = AttributeMap()
        breadcrumbs?.let {
            if (it.size > 1) {
                // Default : book title is the last breadcrumb
                var bookTitle =
                    StringHelper.capitalizeString(it[it.size - 1].text())
                if (it.size > 2) {
                    // Element 1 is always the publisher (using CIRCLE as publisher never appears on the Hentoid UI)
                    val publisher = it[1].text().lowercase(Locale.getDefault())
                    if (!nonLegitPublishers.contains(publisher)) ParseHelper.parseAttribute(
                        attributes, AttributeType.CIRCLE,
                        it[1], false, Site.MUSES
                    )
                    if (it.size > 3) {
                        // Element 2 is either the author or the series, depending on the publisher
                        var type = AttributeType.SERIE
                        if (publishersWithAuthors.contains(publisher)) type = AttributeType.ARTIST
                        ParseHelper.parseAttribute(
                            attributes,
                            type,
                            it[2],
                            false,
                            Site.MUSES
                        )
                        // Add series to book title if it isn't there already
                        if (AttributeType.SERIE === type) {
                            val series = it[2].text()
                            if (!bookTitle.lowercase(Locale.getDefault())
                                    .startsWith(series.lowercase(Locale.getDefault()))
                            ) bookTitle = "$series - $bookTitle"
                        }
                        if (it.size > 4) {
                            // All that comes after element 2 contributes to the book title
                            var first = true
                            val bookTitleBuilder = StringBuilder()
                            for (i in 3 until it.size) {
                                if (first) first = false else bookTitleBuilder.append(" - ")
                                bookTitleBuilder.append(it[i].text())
                            }
                            bookTitle = bookTitleBuilder.toString()
                        }
                    }
                }
                content.setTitle(StringHelper.removeNonPrintableChars(bookTitle))
            }
        }
        if (updateImages) {
            content.setQtyPages(nbImages) // Cover is duplicated in the code below; no need to decrease nbImages here
            var thumbParts: MutableList<String>
            var index = 0
            val images: MutableList<ImageFile> = ArrayList()
            // Cover
            val cover = ImageFile.fromImageUrl(
                index++,
                Site.MUSES.url + imagesUrls[0],
                StatusContent.SAVED,
                nbImages
            )
            content.setCoverImageUrl(cover.url)
            cover.setIsCover(true)
            images.add(cover)
            // Images
            for (u in imagesUrls) {
                thumbParts = u.split("/").toMutableList()
                if (thumbParts.size > 3) {
                    thumbParts[2] =
                        "fl" // Large dimensions; there's also a medium variant available (fm)
                    val imgUrl =
                        Site.MUSES.url + "/" + thumbParts[1] + "/" + thumbParts[2] + "/" + thumbParts[3]
                    images.add(
                        ImageFile.fromImageUrl(
                            index++,
                            imgUrl,
                            StatusContent.SAVED,
                            nbImages
                        )
                    ) // We infer actual book page images have the same format as their thumbs
                }
            }
            content.setImageFiles(images)
        }

        // Tags are not shown on the album page, but on the picture page (!)
        try {
            thumbLinks?.let {
                val doc =
                    HttpHelper.getOnlineDocument(
                        Site.MUSES.url + it[it.size - 1].attr("href")
                    )
                if (doc != null) {
                    val elements = doc.select(".album-tags a[href*='/search/tag']")
                    if (!elements.isEmpty()) ParseHelper.parseAttributes(
                        attributes,
                        AttributeType.TAG,
                        elements,
                        false,
                        Site.MUSES
                    )
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
        content.putAttributes(attributes)
        return content
    }
}