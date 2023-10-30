package me.devsaki.hentoid.util.file

import android.content.Context
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Preferences

object StorageHelper {
    fun isLowDeviceStorage(context: Context): Boolean {
        return isLowDeviceStorage(context, StorageLocation.PRIMARY_1)
                || isLowDeviceStorage(context, StorageLocation.PRIMARY_2)
    }

    private fun isLowDeviceStorage(context: Context, location: StorageLocation): Boolean {
        val rootFolder =
            FileHelper.getDocumentFromTreeUriString(context, Preferences.getStorageUri(location))
                ?: return false
        val freeSpaceRatio = FileHelper.MemoryUsageFigures(context, rootFolder).freeUsageRatio100
        return freeSpaceRatio < 100 - Preferences.getMemoryAlertThreshold()
    }
}