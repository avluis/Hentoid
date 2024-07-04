package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent

@JsonClass(generateAdapter = true)
data class JsonImageFile(
    val order: Int,
    val url: String,
    val pageUrl: String?,
    val name: String,
    val isCover: Boolean?,
    val favourite: Boolean?,
    val isRead: Boolean?,
    val status: StatusContent,
    val mimeType: String?,
    val pHash: Long?,
    val isTransformed: Boolean?,
    val chapterOrder: Int?
) {
    constructor(f: ImageFile) : this(
        f.order,
        f.url,
        f.pageUrl,
        f.name,
        f.isCover,
        f.favourite,
        f.read,
        f.status,
        f.mimeType,
        f.imageHash,
        f.isTransformed,
        f.linkedChapter?.order ?: -1
    )

    fun toEntity(chapters: List<Chapter>): ImageFile {
        var result = ImageFile.fromImageUrl(order, url, status, name)
        if (url.isEmpty()) result = ImageFile.fromPageUrl(order, pageUrl ?: "", status, name)
        result.name = name
        result.isCover = isCover ?: false
        result.favourite = favourite ?: false
        result.read = isRead ?: false
        result.mimeType = mimeType ?: ""
        result.imageHash = pHash ?: 0
        result.isTransformed = isTransformed ?: false

        if (chapters.isNotEmpty() && (chapterOrder ?: -1) > -1) {
            val chapter = chapters.firstOrNull { it.order == chapterOrder }
            if (chapter != null) result.setChapter(chapter)
        }

        return result
    }
}