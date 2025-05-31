package me.devsaki.hentoid.json.sources.pixiv

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.cleanup

/**
 * Data structure for Pixiv's "series-content" mobile endpoint
 */
@JsonClass(generateAdapter = true)
data class PixivSeriesIllustMetadata(
    val error: Boolean? = null,
    val message: String? = null,
    val body: SeriesContentBody? = null
) {
    fun getIllustIdTitles(): List<Pair<String, String>> {
        if (body?.seriesContents == null) return emptyList()
        return body.seriesContents.map { Pair(it.id ?: "", it.title ?: "") }
    }

    fun getChapters(contentId: Long): List<Chapter> {
        val result: MutableList<Chapter> = ArrayList()
        if (body?.seriesContents == null) return result
        for ((order, sc) in body.seriesContents.withIndex()) {
            val forgedUrl = Site.PIXIV.url + "artworks/" + (sc.id ?: "")
            val chp = Chapter(order, forgedUrl, cleanup(sc.title))
            chp.uniqueId = sc.id ?: ""
            chp.setContentId(contentId)
            result.add(chp)
        }
        return result
    }
}

@JsonClass(generateAdapter = true)
data class SeriesContentBody(
    @Json(name = "series_contents")
    val seriesContents: List<SeriesContent>? = null
)

@JsonClass(generateAdapter = true)
data class SeriesContent(
    val id: String? = null,
    val title: String? = null
)