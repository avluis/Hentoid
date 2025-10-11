package me.devsaki.hentoid.json.sources.jikan

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JikanContent(
    @Json(name = "mal_id")
    val id: String,
    val url: String,
    val title: String
)