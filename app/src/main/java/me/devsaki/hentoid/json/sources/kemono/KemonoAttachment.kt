package me.devsaki.hentoid.json.sources.kemono

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KemonoAttachment(
    val server: String?,
    val name: String?,
    val path: String?
)