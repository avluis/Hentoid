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
        results.forEach {res ->
            val imgs = res.getImages()
            result.addAll(imgs.filter { it.isReadable }.map { ImageFile(it, true, true) })
            if (null == result.find { it.isCover }) {
                val cover = ImageFile(imgs.first { it.isCover }, true, true)
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