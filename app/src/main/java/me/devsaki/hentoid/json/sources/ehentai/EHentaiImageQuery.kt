package me.devsaki.hentoid.json.sources.ehentai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EHentaiImageQuery(
    val gid: Int,
    val imgkey: String,
    val mpvkey: String,
    val page: Int,
    val method: String = "imagedispatch"
)