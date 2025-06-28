package me.devsaki.hentoid.json.sources.ehentai

import com.squareup.moshi.Json

data class EHentaiImageResponse(
    // Relative link to the full-size image
    val fullUrlRelative: String? = null,
    val ls: String? = null,
    val ll: String? = null,
    val lo: String? = null,
    // image displayed in the multipage viewer
    @Json(name = "i")
    val url: String,
    val s: String? = null
)