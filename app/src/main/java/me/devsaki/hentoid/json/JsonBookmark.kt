package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.enums.Site

@JsonClass(generateAdapter = true)
data class JsonBookmark(
    val site: Site,
    val title: String,
    val url: String,
    val order: Int = 0
) {
    constructor(b: SiteBookmark) : this(b.site, b.title, b.url, b.order)

    fun toEntity(): SiteBookmark {
        val result = SiteBookmark(site, title, url)
        result.order = order
        return result
    }
}