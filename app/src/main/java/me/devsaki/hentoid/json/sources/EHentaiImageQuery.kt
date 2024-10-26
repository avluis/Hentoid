package me.devsaki.hentoid.json.sources

data class EHentaiImageQuery(
    val gid: Int,
    val imgkey: String,
    val mpvkey: String,
    val page: Int,
    val method: String = "imagedispatch"
)