package me.devsaki.hentoid.workers.data

import android.net.Uri
import androidx.work.Data
import me.devsaki.hentoid.util.Preferences

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DownloadsImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_FILE_URI = "file_uri"
private const val KEY_QUEUE_POSITION = "queue_position"
private const val KEY_IMPORT_AS_STREAMED = "as_streamed"

class DownloadsImportData {

    class Builder {
        private val builder = Data.Builder()
        fun setFileUri(data: Uri) {
            builder.putString(KEY_FILE_URI, data.toString())
        }

        fun setQueuePosition(data: Int) {
            builder.putInt(KEY_QUEUE_POSITION, data)
        }

        fun setImportAsStreamed(data: Boolean) {
            builder.putBoolean(KEY_IMPORT_AS_STREAMED, data)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val fileUri: String
            get() = data.getString(KEY_FILE_URI) ?: ""
        val queuePosition: Int
            get() = data.getInt(
                KEY_QUEUE_POSITION,
                Preferences.Default.QUEUE_NEW_DOWNLOADS_POSITION
            )
        val importAsStreamed: Boolean
            get() = data.getBoolean(KEY_IMPORT_AS_STREAMED, false)
    }
}