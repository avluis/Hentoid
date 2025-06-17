package me.devsaki.hentoid.json.sources.kemono

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KemonoArtist(
    val id: String,
    val name: String,
    val service: String
)