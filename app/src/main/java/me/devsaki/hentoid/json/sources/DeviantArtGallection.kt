package me.devsaki.hentoid.json.sources

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import kotlin.math.floor
import kotlin.math.log10

@JsonClass(generateAdapter = true)
data class DeviantArtGallection(
    val hasMore: Boolean,
    val nextOffset: Int?,
    val results: List<DeviantArtDeviation.Deviation>
) {
    fun update(content: Content, updateImages: Boolean): Content {
        var result = content
        val images: MutableList<ImageFile> = ArrayList()
        results.forEachIndexed { index, it ->
            result = it.update(content, updateImages, false)
            images.addAll(result.imageList.filter { i -> i.isReadable }.map { i -> ImageFile(i) })
            if (0 == index) {
                val cover = ImageFile(result.imageList.first { i -> i.isCover })
                images.add(cover)
                result.coverImageUrl = cover.url
            }
        }
        images.forEachIndexed { index, imageFile ->
            imageFile.order = index + 1
            imageFile.computeName(floor(log10(images.size.toDouble()) + 1).toInt())
        }
        result.setImageFiles(images)
        result.qtyPages = images.size - 1
        return result
    }
}