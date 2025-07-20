package me.devsaki.hentoid.json

import android.webkit.MimeTypeMap
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class GithubRelease(
    @param:Json(name = "tag_name")
    val tagName: String,
    val name: String,
    val body: String,
    @param:Json(name = "created_at")
    val creationDate: Date,
    val assets: List<GithubAsset>,
    val prerelease: Boolean,
    val draft: Boolean
) {
    fun isPublished(): Boolean {
        return !prerelease && !draft
    }

    fun getApkAssetUrl(): String {
        val apkMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk")
        for (asset in assets) {
            if (asset.contentType.equals(apkMimeType, ignoreCase = true))
                return asset.browserDownloadUrl
        }
        return ""
    }


    @JsonClass(generateAdapter = true)
    data class GithubAsset(
        @param:Json(name = "browser_download_url")
        val browserDownloadUrl: String,
        @param:Json(name = "content_type")
        val contentType: String
    )
}