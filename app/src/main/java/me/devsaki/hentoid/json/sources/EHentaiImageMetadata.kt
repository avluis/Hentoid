package me.devsaki.hentoid.json.sources

data class EHentaiImageMetadata(
    val n: String,
    val k: String,
    val t: String
) {
    fun getKey(): String {
        return k
    }
}