package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent


data class LusciousGalleryMetadata(
    val data: PictureData
) {
    data class PictureData(
        val picture: PictureInfoContainer
    )

    data class PictureInfoContainer(
        val list: PictureInfo
    )

    data class PictureInfo(
        val info: PictureContainerMetadata,
        val items: List<PictureMetadata>
    )

    data class PictureContainerMetadata(
        @Json(name = "total_pages")
        var totalPages: Int = 0
    )

    data class PictureMetadata(
        @Json(name = "url_to_original")
        val urlToOriginal: String
    )

    fun getNbPages(): Int {
        return data.picture.list.info.totalPages
    }

    fun toImageFileList(offset: Int = 0): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        var order = offset
        val imageList: List<PictureMetadata> = data.picture.list.items
        imageList.forEach {
            result.add(
                ImageFile.fromImageUrl(
                    ++order,
                    it.urlToOriginal,
                    StatusContent.SAVED,
                    imageList.size
                )
            )
        }
        return result
    }
}
