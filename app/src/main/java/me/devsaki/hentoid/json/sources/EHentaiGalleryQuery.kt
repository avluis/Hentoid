package me.devsaki.hentoid.json.sources

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EHentaiGalleryQuery(
    val gidlist: List<List<String>> = ArrayList(),
    val method: String = "gdata",
    val namespace: String = "1"
) {
    constructor(galleryId: String, galleryKey: String) : this(
        listOf(listOf(galleryId, galleryKey))
    )
}
