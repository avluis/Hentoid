package me.devsaki.hentoid.workers.data

import android.os.Bundle
import androidx.work.Data
import me.devsaki.hentoid.util.fromByteArray
import me.devsaki.hentoid.util.toByteArray
import me.devsaki.hentoid.workers.BaseDeleteWorker

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DeleteWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_CONTENT_IDS = "contentIds"
private const val KEY_CONTENT_PURGE_KEEPCOVERS = "contentPurgeKeepCovers"
private const val KEY_GROUP_IDS = "groupIds"
private const val KEY_QUEUE_IDS = "queueIds"
private const val KEY_IMAGE_IDS = "imageIds"
private const val KEY_DELETE_ALL_QUEUE_RECORDS = "deleteAllQueueRecords"
private const val KEY_DELETE_GROUPS_ONLY = "deleteGroupsOnly"
private const val KEY_OPERATION = "operation"
private const val KEY_CONTENT_FILTER = "contentFilter"
private const val KEY_INVERT_FILTER_SCOPE = "invertFilterScope"
private const val KEY_KEEP_FAV_GROUPS = "keepFavGroups"
private const val KEY_DL_PREPURGE = "downloadPrepurge"

class DeleteData {
    class Builder {
        private val builder = Data.Builder()
        fun setContentIds(value: List<Long>) {
            builder.putLongArray(KEY_CONTENT_IDS, value.toLongArray())
        }

        fun setContentPurgeKeepCovers(value: Boolean) {
            builder.putBoolean(KEY_CONTENT_PURGE_KEEPCOVERS, value)
        }

        fun setGroupIds(value: List<Long>) {
            builder.putLongArray(KEY_GROUP_IDS, value.toLongArray())
        }

        fun setQueueIds(value: List<Long>) {
            builder.putLongArray(KEY_QUEUE_IDS, value.toLongArray())
        }

        fun setImageIds(value: List<Long>) {
            builder.putLongArray(KEY_IMAGE_IDS, value.toLongArray())
        }

        fun setDeleteAllQueueRecords(value: Boolean) {
            builder.putBoolean(KEY_DELETE_ALL_QUEUE_RECORDS, value)
        }

        fun setDeleteGroupsOnly(value: Boolean) {
            builder.putBoolean(KEY_DELETE_GROUPS_ONLY, value)
        }

        fun setOperation(value: BaseDeleteWorker.Operation) {
            builder.putInt(KEY_OPERATION, value.ordinal)
        }

        fun setContentFilter(value: Bundle) {
            val data = value.toByteArray()
            builder.putByteArray(KEY_CONTENT_FILTER, data)
        }

        fun setInvertFilterScope(value: Boolean?) {
            builder.putBoolean(KEY_INVERT_FILTER_SCOPE, value!!)
        }

        fun setKeepFavGroups(value: Boolean?) {
            builder.putBoolean(KEY_KEEP_FAV_GROUPS, value!!)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val operation: BaseDeleteWorker.Operation?
            get() = BaseDeleteWorker.Operation.entries.firstOrNull {
                it.ordinal == data.getInt(KEY_OPERATION, -1)
            }
        val contentIds: LongArray
            get() {
                return data.getLongArray(KEY_CONTENT_IDS) ?: longArrayOf()
            }
        val contentPurgeKeepCovers: Boolean
            get() = data.getBoolean(KEY_CONTENT_PURGE_KEEPCOVERS, false)
        val groupIds: LongArray
            get() {
                return data.getLongArray(KEY_GROUP_IDS) ?: longArrayOf()
            }
        val queueIds: LongArray
            get() {
                return data.getLongArray(KEY_QUEUE_IDS) ?: longArrayOf()
            }
        val imageIds: LongArray
            get() {
                return data.getLongArray(KEY_IMAGE_IDS) ?: longArrayOf()
            }
        val isDeleteAllQueueRecords: Boolean
            get() = data.getBoolean(KEY_DELETE_ALL_QUEUE_RECORDS, false)
        val isDeleteGroupsOnly: Boolean
            get() = data.getBoolean(KEY_DELETE_GROUPS_ONLY, false)
        val contentFilter: Bundle
            get() = data.getByteArray(KEY_CONTENT_FILTER)
                ?.let { Bundle().fromByteArray(it) }
                ?: Bundle()
        val isInvertFilterScope: Boolean
            get() = data.getBoolean(KEY_INVERT_FILTER_SCOPE, false)
        val isKeepFavGroups: Boolean
            get() = data.getBoolean(KEY_KEEP_FAV_GROUPS, false)
    }
}