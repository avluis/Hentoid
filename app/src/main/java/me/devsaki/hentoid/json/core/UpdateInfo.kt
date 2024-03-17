package me.devsaki.hentoid.json.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.enums.AlertStatus
import me.devsaki.hentoid.enums.AlertStatus.Companion.searchByName
import me.devsaki.hentoid.enums.Site

/**
 * Structure to describe Hentoid update informations
 * Original source is stored on app/update.json
 */
@JsonClass(generateAdapter = true)
data class UpdateInfo(
    @Json(name = "updateURL")
    val updateUrl: String,
    val versionCode: Int,
    @Json(name = "updateURL.beta")
    val updateUrlDebug: String?,
    @Json(name = "versionCode.beta")
    val versionCodeDebug: Int?,
    @Json(name = "sourceAlerts")
    val sourceAlerts: List<SourceAlert>,
    @Json(name = "sourceAlerts.beta")
    val sourceAlertsDebug: List<SourceAlert>?
) {
    @JsonClass(generateAdapter = true)
    data class SourceAlert(
        val sourceName: String,
        val status: String,
        val fixedByBuild: String?
    ) {
        fun getSite(): Site {
            return Site.searchByName(sourceName)
        }

        fun getStatus(): AlertStatus {
            return searchByName(status)
        }

        fun getFixedByBuild(): Int {
            return if (fixedByBuild.isNullOrEmpty()) Int.MAX_VALUE else fixedByBuild.toInt()
        }
    }


    fun getUpdateUrl(isDebug: Boolean): String {
        return if (isDebug) updateUrlDebug ?: "" else updateUrl
    }

    fun getVersionCode(isDebug: Boolean): Int {
        return if (isDebug) versionCodeDebug ?: 0 else versionCode
    }

    fun getSourceAlerts(isDebug: Boolean): List<SourceAlert> {
        return if (isDebug) sourceAlertsDebug ?: emptyList() else sourceAlerts
    }
}