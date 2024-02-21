package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviantArtDeviation(
    val deviation: Deviation
) {
    @JsonClass(generateAdapter = true)
    data class Deviation(
        val title: String,
        val publishedTime: String,
        val author: Author,
        val media: Media,
        val extended: ExtendedData
    )

    @JsonClass(generateAdapter = true)
    data class Author(
        val username: String
    )

    @JsonClass(generateAdapter = true)
    data class Media(
        val baseUri: String,
        val prettyName: String,
        val token: List<String>,
        val types: List<MediaType>
    )

    @JsonClass(generateAdapter = true)
    data class MediaType(
        @Json(name = "t")
        val title: String,
        @Json(name = "c")
        val path: String?, // Not present for fullview
        @Json(name = "h")
        val height: Int,
        @Json(name = "w")
        val width: Int,
    )

    @JsonClass(generateAdapter = true)
    data class ExtendedData(
        val tags: List<Tag>,
        val download: DownloadData?
    )

    @JsonClass(generateAdapter = true)
    data class Tag(
        val name: String,
        val url: String
    )

    @JsonClass(generateAdapter = true)
    data class DownloadData(
        val url: String
    )
}
