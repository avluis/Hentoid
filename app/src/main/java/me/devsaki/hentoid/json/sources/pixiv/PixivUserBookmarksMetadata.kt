package me.devsaki.hentoid.json.sources.pixiv

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent


/**
 * Data structure for Pixiv's "user bookmarks" mobile endpoint
 */
data class PixivUserBookmarksMetadata(
    private val error: Boolean,
    private val message: String,
    private val body: PixivUserBookmarks? = null
) {
    @JsonClass(generateAdapter = true)
    data class PixivUserBookmarks(
        @Json(name = "bookmarks")
        val bookmarks: List<BookmarkData>? = null
    )

    @JsonClass(generateAdapter = true)
    data class BookmarkData(
        val id: String,
        @Json(name = "user_id")
        val userId: String,
        val visible: Boolean,
        val title: String? = null,
        val tags: List<String>? = null,
        val url: String? = null,
        @Json(name = "url_s")
        val urlSmall: String? = null
    ) {
        val thumbUrl: String
            get() {
                var result = url
                if (null == result || result.isEmpty()) result = urlSmall
                return result ?: ""
            }
    }

    fun getIllustIds(): List<String> {
        if (null == body || null == body.bookmarks) return emptyList()
        return body.bookmarks.filter { it.visible }
            .map { it.id }
    }

    fun isError(): Boolean {
        return error
    }

    fun getMessage(): String {
        return message
    }

    fun update(content: Content, userId: String, updateImages: Boolean): Content {
        content.site = Site.PIXIV
        if (error || null == body || null == body.bookmarks) {
            content.status = StatusContent.IGNORED
            return content
        }
        content.title = "Pixiv bookmarks"
        val data = body.bookmarks
        content.uniqueSiteId = "bookmarks/$userId"
        content.url = "users/$userId/bookmarks/artworks"
        content.coverImageUrl = data.firstOrNull()?.thumbUrl ?: ""
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}
