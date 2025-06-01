package me.devsaki.hentoid.json.sources.pixiv

import com.squareup.moshi.JsonClass

/**
 * Data structure for Pixiv's "illust pages" desktop endpoint
 */
@JsonClass(generateAdapter = true)
data class PixivIllustPagesMetadata(
    val error: Boolean? = null,
    val message: String? = null,
    val body: List<PixivImage>? = null
) {
    fun getPageUrls(): List<String> {
        return body?.map { it.pageUrl } ?: emptyList()
    }

    @JsonClass(generateAdapter = true)
    data class PixivImage(
        val urls: Map<String, String>? = null
    ) {
        val pageUrl: String
            get() {
                if (null == urls) return ""
                var result = urls["original"]
                if (null == result) result = urls["regular"]
                return result ?: ""
            }
    }
}