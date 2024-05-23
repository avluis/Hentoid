package me.devsaki.hentoid.workers.data

import androidx.work.Data

/**
 * Helper class to transfer data from any Activity to {@link ExternalImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_BEHOLD = "behold"

class ExternalImportData {

    class Builder {
        private val builder = Data.Builder()
        fun setBehold(data: Boolean) {
            builder.putBoolean(KEY_BEHOLD, data)
        }

        val data: Data
            get() = builder.build()
    }

    class Parser(private val data: Data) {
        val behold: Boolean
            get() = data.getBoolean(KEY_BEHOLD, false)
    }
}