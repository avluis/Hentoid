package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JsonSettings(
    var settings: Map<String, Any> = HashMap()
)