package me.devsaki.hentoid.workers.data

import android.os.Bundle
import androidx.work.Data
import me.devsaki.hentoid.util.fromByteArray
import me.devsaki.hentoid.util.toByteArray

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DeleteWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val KEY_CONTENT_IDS = "contentIds"
private const val KEY_CONTENT_PURGE_IDS = "contentPurgeIds"
private const val KEY_CONTENT_PURGE_KEEPCOVERS = "contentPurgeKeepCovers"
private const val KEY_GROUP_IDS = "groupIds"
private const val KEY_QUEUE_IDS = "queueIds"
private const val KEY_IMAGE_IDS = "imageIds"
private const val KEY_DELETE_ALL_QUEUE_RECORDS = "deleteAllQueueRecords"
private const val KEY_DELETE_GROUPS_ONLY = "deleteGroupsOnly"
private const val KEY_MASS_OPERATION = "massOperation"
private const val KEY_MASS_FILTER = "massFilter"
private const val KEY_MASS_INVERT_SCOPE = "massInvertScope"
private const val KEY_MASS_KEEP_FAV_GROUPS = "massKeepFavGroups"
private const val KEY_DL_PREPURGE = "downloadPrepurge"

class DeleteData {
    class Builder {
        private val builder = Data.Builder()
        fun setContentIds(value: List<Long>) {
            builder.putLongArray(KEY_CONTENT_IDS, value.toLongArray())
        }

        fun setContentPurgeIds(value: List<Long>) {
            builder.putLongArray(KEY_CONTENT_PURGE_IDS, value.toLongArray())
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

        fun setMassOperation(value: Int) {
            builder.putInt(KEY_MASS_OPERATION, value)
        }

        fun setMassFilter(value: Bundle) {
            val data = value.toByteArray()
            builder.putByteArray(KEY_MASS_FILTER, data)
        }

        fun setMassInvertScope(value: Boolean?) {
            builder.putBoolean(KEY_MASS_INVERT_SCOPE, value!!)
        }

        fun setMassKeepFavGroups(value: Boolean?) {
            builder.putBoolean(KEY_MASS_KEEP_FAV_GROUPS, value!!)
        }

        fun setDownloadPrepurge(value: Boolean) {
            builder.putBoolean(KEY_DL_PREPURGE, value)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val contentIds: LongArray
            get() {
                return data.getLongArray(KEY_CONTENT_IDS) ?: longArrayOf()
            }
        val contentPurgeIds: LongArray
            get() {
                return data.getLongArray(KEY_CONTENT_PURGE_IDS) ?: longArrayOf()
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
        val massOperation: Int
            get() = data.getInt(KEY_MASS_OPERATION, -1)
        val massFilter: Bundle
            get() = data.getByteArray(KEY_MASS_FILTER)
                ?.let { Bundle().fromByteArray(it) }
                ?: Bundle()
        val isMassInvertScope: Boolean
            get() = data.getBoolean(KEY_MASS_INVERT_SCOPE, false)
        val isMassKeepFavGroups: Boolean
            get() = data.getBoolean(KEY_MASS_KEEP_FAV_GROUPS, false)
        val isDownloadPrepurge: Boolean
            get() = data.getBoolean(KEY_DL_PREPURGE, false)
    }
}