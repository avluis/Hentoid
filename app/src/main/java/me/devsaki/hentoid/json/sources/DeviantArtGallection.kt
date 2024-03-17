package me.devsaki.hentoid.json.sources

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.ImageFile
import kotlin.math.floor
import kotlin.math.log10

@JsonClass(generateAdapter = true)
data class DeviantArtGallection(
    val hasMore: Boolean,
    val nextOffset: Int?,
    val results: List<DeviantArtDeviation.Deviation>
) {
    fun getImages(): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        results.forEach {
            val imgs = it.getImages()
            result.addAll(imgs.filter { i -> i.isReadable }.map { i -> ImageFile(i) })
            if (null == result.find { i -> i.isCover }) {
                val cover = ImageFile(imgs.first { i -> i.isCover })
                result.add(0, cover)
            }
        }
        var idx = 1
        result.forEach { imageFile ->
            if (!imageFile.isCover) {
                imageFile.order = idx++
                imageFile.computeName(floor(log10(result.size.toDouble()) + 1).toInt())
            }
        }
        return result
    }
}