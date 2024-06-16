package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.Site.SiteConverter
import java.util.Objects

@Entity
data class SiteBookmark(
    @Id
    var id: Long = 0,
    @Convert(converter = SiteConverter::class, dbType = Long::class)
    val site: Site,
    var title: String,
    val url: String,
    var order: Int = -1,
    var isHomepage: Boolean = false
) {
    constructor() : this(0, Site.NONE, "", "")

    constructor(site: Site, title: String, url: String) : this(
        0, site, title, url
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SiteBookmark
        return urlsAreSame(url, that.url)
    }

    override fun hashCode(): Int {
        return Objects.hash(neutralizeUrl(url))
    }

    companion object {
        fun neutralizeUrl(url: String?): String {
            if (null == url) return ""
            return if (url.endsWith("/")) url.substring(0, url.length - 1) else url
        }

        // Quick comparator to avoid host/someurl and host/someurl/ to be considered as different by the bookmarks managaer
        fun urlsAreSame(url1: String?, url2: String?): Boolean {
            return neutralizeUrl(url1).equals(neutralizeUrl(url2), ignoreCase = true)
        }
    }
}