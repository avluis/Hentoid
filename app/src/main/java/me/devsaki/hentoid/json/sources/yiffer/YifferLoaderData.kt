package me.devsaki.hentoid.json.sources.yiffer

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.urlsToImageFiles

@JsonClass(generateAdapter = true)
data class YifferLoaderData(
    val loaderData: YifferData
) {
    fun update(c: Content, updateImages: Boolean) {
        loaderData.update(c, updateImages)
    }
}

@JsonClass(generateAdapter = true)
data class YifferData(
    @Json(name = "routes/pages/comic/ComicPage") // Yes...
    val contentData: YifferContentData
) {
    private fun getPageUrls(): List<String> {
        val comic = contentData.comic ?: contentData.data!!.comic
        val pagesPath = contentData.pagesPath ?: contentData.data!!.pagesPath ?: ""
        return comic.pages.sortedBy { it.pageNumber }
            .map { "$pagesPath/comics/${comic.id}/${it.token}.jpg" }
    }

    fun update(c: Content, updateImages: Boolean) {
        val comic = contentData.comic ?: contentData.data!!.comic
        c.title = comic.name
        c.uploadDate = comic.published
        c.status = StatusContent.SAVED

        val attributes = AttributeMap()
        comic.artist?.let {
            attributes.add(
                Attribute(
                    AttributeType.ARTIST,
                    it.name,
                    "",
                    Site.YIFFER
                )
            )
        }
        comic.tags?.forEach {
            attributes.add(
                Attribute(
                    AttributeType.TAG,
                    it.name,
                    "",
                    Site.YIFFER
                )
            )
        }

        c.putAttributes(attributes)

        if (updateImages) {
            val pageUrls = getPageUrls()
            c.setImageFiles(urlsToImageFiles(pageUrls, pageUrls[0], StatusContent.SAVED))
            c.qtyPages = pageUrls.size
        }
    }
}

@JsonClass(generateAdapter = true)
data class YifferContentData(
    val data: YifferComicDataContainer?,
    val comic: YifferComicData?,
    @Json(name = "PAGES_PATH")
    val pagesPath: String?
)

@JsonClass(generateAdapter = true)
data class YifferComicDataContainer(
    val comic: YifferComicData,
    @Json(name = "PAGES_PATH")
    val pagesPath: String?
)

@JsonClass(generateAdapter = true)
data class YifferComicData(
    val id: Int,
    val name: String,
    val state: String,
    val published: Long,
    val artist: YifferItem?,
    val tags: List<YifferItem>?,
    val pages: List<YifferPage>
)

@JsonClass(generateAdapter = true)
data class YifferItem(
    val id: Int,
    val name: String
)

@JsonClass(generateAdapter = true)
data class YifferPage(
    val token: String,
    val pageNumber: Int
)