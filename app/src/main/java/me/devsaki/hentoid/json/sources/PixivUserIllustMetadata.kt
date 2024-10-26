package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


/**
 * Data structure for Pixiv's "user illusts" mobile endpoint
 */
data class PixivUserIllustMetadata(
    private val error: Boolean? = null,
    private val message: String? = null,
    private val body: PixivUserIllusts? = null
) {
    fun getIllustIds(): List<String> {
        return body?.userIllustIds ?: emptyList()
    }

    fun isError(): Boolean {
        return error ?: false
    }

    fun getMessage(): String {
        return message ?: ""
    }

    @JsonClass(generateAdapter = true)
    data class PixivUserIllusts(
        @Json(name = "user_illust_ids")
        val userIllustIds: List<String>? = null
    )
}
