package me.devsaki.hentoid.util.file

import android.content.Context
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Preferences

fun Context.isLowDeviceStorage(threshold: Int? = null): Boolean {
    return isLowDeviceStorage(StorageLocation.PRIMARY_1, threshold)
            || isLowDeviceStorage(StorageLocation.PRIMARY_2, threshold)
}

private fun Context.isLowDeviceStorage(
    location: StorageLocation,
    threshold: Int?
): Boolean {
    val rootFolder =
        FileHelper.getDocumentFromTreeUriString(this, Preferences.getStorageUri(location))
            ?: return false
    val freeSpaceRatio = FileHelper.MemoryUsageFigures(this, rootFolder).freeUsageRatio100
    return freeSpaceRatio < 100 - (threshold ?: Preferences.getMemoryAlertThreshold())
}