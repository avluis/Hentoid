package me.devsaki.hentoid.workers.data

import androidx.work.Data

/**
 * Helper class to transfer data from any Activity to {@link ExternalImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_BEHOLD = "behold"
private const val KEY_FOLDERS = "folders"

class ExternalImportData {

    class Builder {
        private val builder = Data.Builder()
        fun setBehold(data: Boolean) {
            builder.putBoolean(KEY_BEHOLD, data)
        }

        fun setFolders(data: List<String>) {
            builder.putStringArray(KEY_FOLDERS, data.toTypedArray())
        }

        val data: Data
            get() = builder.build()
    }

    class Parser(private val data: Data) {
        val behold: Boolean
            get() = data.getBoolean(KEY_BEHOLD, false)
        val folders: List<String>
            get() = data.getStringArray(KEY_FOLDERS)?.toList() ?: emptyList()
    }
}