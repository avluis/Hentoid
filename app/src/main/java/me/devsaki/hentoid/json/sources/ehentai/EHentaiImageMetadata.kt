package me.devsaki.hentoid.json.sources.ehentai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EHentaiImageMetadata(
    val n: String,
    val k: String,
    val t: String
) {
    fun getKey(): String {
        return k
    }
}