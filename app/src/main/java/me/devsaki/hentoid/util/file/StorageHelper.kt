package me.devsaki.hentoid.util.file

import android.content.Context
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Preferences

object StorageHelper {
    fun isLowDeviceStorage(context: Context, threshold: Int? = null): Boolean {
        return isLowDeviceStorage(context, StorageLocation.PRIMARY_1, threshold)
                || isLowDeviceStorage(context, StorageLocation.PRIMARY_2, threshold)
    }

    private fun isLowDeviceStorage(
        context: Context,
        location: StorageLocation,
        threshold: Int?
    ): Boolean {
        val rootFolder =
            FileHelper.getDocumentFromTreeUriString(context, Preferences.getStorageUri(location))
                ?: return false
        val freeSpaceRatio = FileHelper.MemoryUsageFigures(context, rootFolder).freeUsageRatio100
        return freeSpaceRatio < 100 - (threshold ?: Preferences.getMemoryAlertThreshold())
    }
}