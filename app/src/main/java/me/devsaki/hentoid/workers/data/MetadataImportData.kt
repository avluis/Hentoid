package me.devsaki.hentoid.workers.data

import androidx.work.Data

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.MetadataImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_JSON_URI = "jsonUri"
private const val KEY_ADD = "add"
private const val KEY_IMPORT_LIBRARY = "importLibrary"
private const val KEY_EMPTY_BOOKS_OPTION = "emptyBooksOption"
private const val KEY_IMPORT_QUEUE = "importQueue"
private const val KEY_IMPORT_CUSTOM_GROUPS = "importCustomGroups"
private const val KEY_IMPORT_BOOKMARKS = "importBookmarks"

class MetadataImportData {

    class Builder {
        private val builder = Data.Builder()
        fun setJsonUri(data: String?) {
            builder.putString(KEY_JSON_URI, data)
        }

        fun setIsAdd(data: Boolean) {
            builder.putBoolean(KEY_ADD, data)
        }

        fun setIsImportLibrary(data: Boolean) {
            builder.putBoolean(KEY_IMPORT_LIBRARY, data)
        }

        fun setEmptyBooksOption(data: Int) {
            builder.putInt(KEY_EMPTY_BOOKS_OPTION, data)
        }

        fun setIsImportQueue(data: Boolean) {
            builder.putBoolean(KEY_IMPORT_QUEUE, data)
        }

        fun setIsImportCustomGroups(data: Boolean) {
            builder.putBoolean(KEY_IMPORT_CUSTOM_GROUPS, data)
        }

        fun setIsImportBookmarks(data: Boolean) {
            builder.putBoolean(KEY_IMPORT_BOOKMARKS, data)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val jsonUri: String?
            get() = data.getString(KEY_JSON_URI)
        val isAdd: Boolean
            get() = data.getBoolean(KEY_ADD, false)
        val isImportLibrary: Boolean
            get() = data.getBoolean(KEY_IMPORT_LIBRARY, false)
        val emptyBooksOption: Int
            get() = data.getInt(KEY_EMPTY_BOOKS_OPTION, -1)
        val isImportQueue: Boolean
            get() = data.getBoolean(KEY_IMPORT_QUEUE, false)
        val isImportCustomGroups: Boolean
            get() = data.getBoolean(KEY_IMPORT_CUSTOM_GROUPS, false)
        val isImportBookmarks: Boolean
            get() = data.getBoolean(KEY_IMPORT_BOOKMARKS, false)
    }
}