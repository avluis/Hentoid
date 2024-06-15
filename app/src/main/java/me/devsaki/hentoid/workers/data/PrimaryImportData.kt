package me.devsaki.hentoid.workers.data

import androidx.work.Data
import me.devsaki.hentoid.enums.StorageLocation

/**
 * Helper class to transfer data from any Activity to {@link PrimaryImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_REFRESH_RENAME = "rename"
private const val KEY_REFRESH_REMOVE_PLACEHOLDERS = "removePlaceholders"
private const val KEY_REFRESH_RENUMBER_PAGES = "renumberPages"
private const val KEY_REFRESH_CLEAN_NO_JSON = "cleanNoJson"
private const val KEY_REFRESH_CLEAN_NO_IMAGES = "cleanNoImages"
private const val KEY_IMPORT_GROUPS = "importGroups"

private const val KEY_LOCATION = "location"

private const val KEY_TARGET_ROOT = "targetRoot"

class PrimaryImportData {

    class Builder {
        private val builder = Data.Builder()
        fun setRefreshRename(rename: Boolean) {
            builder.putBoolean(KEY_REFRESH_RENAME, rename)
        }

        fun setRefreshRemovePlaceholders(data: Boolean) {
            builder.putBoolean(KEY_REFRESH_REMOVE_PLACEHOLDERS, data)
        }

        fun setRenumberPages(data: Boolean) {
            builder.putBoolean(KEY_REFRESH_RENUMBER_PAGES, data)
        }

        fun setRefreshCleanNoJson(refresh: Boolean) {
            builder.putBoolean(KEY_REFRESH_CLEAN_NO_JSON, refresh)
        }

        fun setRefreshCleanNoImages(refresh: Boolean) {
            builder.putBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, refresh)
        }

        fun setImportGroups(value: Boolean) {
            builder.putBoolean(KEY_IMPORT_GROUPS, value)
        }

        fun setLocation(value: StorageLocation) {
            builder.putInt(KEY_LOCATION, value.ordinal)
        }

        fun setTargetRoot(value: String?) {
            builder.putString(KEY_TARGET_ROOT, value)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val refreshRename: Boolean
            get() = data.getBoolean(KEY_REFRESH_RENAME, false)
        val refreshRemovePlaceholders: Boolean
            get() = data.getBoolean(KEY_REFRESH_REMOVE_PLACEHOLDERS, false)
        val refreshRenumberPages: Boolean
            get() = data.getBoolean(KEY_REFRESH_RENUMBER_PAGES, false)
        val refreshCleanNoJson: Boolean
            get() = data.getBoolean(KEY_REFRESH_CLEAN_NO_JSON, false)
        val refreshCleanNoImages: Boolean
            get() = data.getBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, false)
        val importGroups: Boolean
            get() = data.getBoolean(KEY_IMPORT_GROUPS, true)
        val location: StorageLocation
            get() = StorageLocation.entries[data.getInt(
                KEY_LOCATION,
                StorageLocation.NONE.ordinal
            )]
        val targetRoot: String
            get() = data.getString(KEY_TARGET_ROOT) ?: ""
    }
}