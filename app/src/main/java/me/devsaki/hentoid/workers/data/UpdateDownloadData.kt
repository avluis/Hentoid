package me.devsaki.hentoid.workers.data

import android.os.Bundle
import androidx.work.Data

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.UpdateDownloadWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_URL = "url"

class UpdateDownloadData {

    class Builder {
        private val builder = Data.Builder()
        val bundle = Bundle()

        fun setUrl(data: String?): Builder {
            builder.putString(KEY_URL, data)
            bundle.putString(KEY_URL, data)
            return this
        }

        val data: Data
            get() = builder.build()
    }


    class Parser {
        private val data: Data?
        private val bundle: Bundle?

        constructor(data: Data) {
            this.data = data
            bundle = null
        }

        constructor(bundle: Bundle) {
            this.bundle = bundle
            data = null
        }

        val url: String
            get() {
                var result: String? = null
                if (data != null) result = data.getString(KEY_URL)
                if (null == result && bundle != null) result = bundle.getString(KEY_URL)
                return result ?: ""
            }
    }
}