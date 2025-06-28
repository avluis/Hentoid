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
        f.imageHash,
        f.isTransformed,
        f.linkedChapter?.order ?: -1
    )

    fun toEntity(chapters: List<Chapter>): ImageFile {
        val result = if (url.isEmpty()) ImageFile.fromPageUrl(order, pageUrl ?: "", status, name)
        else ImageFile.fromImageUrl(order, url, status, name)
        result.name = name
        result.isCover = isCover == true
        result.favourite = favourite == true
        result.read = isRead == true
        result.imageHash = pHash ?: 0
        result.isTransformed = isTransformed == true

        if (chapters.isNotEmpty() && (chapterOrder ?: -1) > -1) {
            chapters.firstOrNull { it.order == chapterOrder }?.let {
                result.setChapter(it)
                it.imageFiles.add(result)
            }
        }

        return result
    }
}