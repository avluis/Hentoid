package me.devsaki.hentoid.workers.data

import androidx.work.Data

/**
 * Helper class to transfer data from any Activity to {@link PrimaryImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_IDS = "content_ids"
private const val KEY_UPDATE_MISSING_DL_DATE = "update_missing_dl_date"
private const val KEY_UPDATE_GROUPS = "update_groups"

class UpdateJsonData {

    class Builder {
        private val builder = Data.Builder()
        fun setContentIds(data: LongArray) {
            builder.putLongArray(KEY_IDS, data)
        }

        fun setUpdateGroups(data: Boolean) {
            builder.putBoolean(KEY_UPDATE_GROUPS, data)
        }

        fun setUpdateMissingDlDate(data: Boolean) {
            builder.putBoolean(KEY_UPDATE_MISSING_DL_DATE, data)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val contentIds: LongArray?
            get() = data.getLongArray(KEY_IDS)
        val updateGroups: Boolean
            get() = data.getBoolean(KEY_UPDATE_GROUPS, false)
        val updateMissingDlDate: Boolean
            get() = data.getBoolean(KEY_UPDATE_MISSING_DL_DATE, false)
    }

}