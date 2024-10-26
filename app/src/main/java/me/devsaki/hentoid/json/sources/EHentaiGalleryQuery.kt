package me.devsaki.hentoid.json.sources

data class EHentaiGalleryQuery(
    val gidlist: List<List<String>> = ArrayList(),
    val method: String = "gdata",
    val namespace: String = "1"
) {
    constructor(galleryId: String, galleryKey: String) : this(
        listOf(listOf(galleryId, galleryKey))
    )
}
