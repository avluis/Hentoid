package me.devsaki.hentoid.workers.data

import androidx.work.Data

private const val KEY_OPERATION = "operation"
private const val KEY_CONTENT_IDS = "contentIds"
private const val KEY_DELETE_AFTER_OPS = "deletAfter"
private const val KEY_NEW_TITLE = "newTitle"
private const val KEY_USE_BOOKS_AS_CHAPTERS = "useBooksAsChapters"
private const val KEY_CHAPTER_IDS_FOR_SPLIT = "chapterIdsForSplit"

class SplitMergeData {
    class Builder {
        private val builder = Data.Builder()

        // 0 = Split; 1 = Merge
        fun setOperation(value: Int) {
            builder.putInt(KEY_OPERATION, value)
        }

        fun setContentIds(value: List<Long>) {
            builder.putLongArray(KEY_CONTENT_IDS, value.toLongArray())
        }

        fun setChapterIdsForSplit(value: List<Long>) {
            builder.putLongArray(KEY_CHAPTER_IDS_FOR_SPLIT, value.toLongArray())
        }

        fun setDeleteAfterOps(value: Boolean) {
            builder.putBoolean(KEY_DELETE_AFTER_OPS, value)
        }

        fun setNewTitle(value: String) {
            builder.putString(KEY_NEW_TITLE, value)
        }

        fun setUseBooksAsChapters(value: Boolean) {
            builder.putBoolean(KEY_USE_BOOKS_AS_CHAPTERS, value)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val operation: Int
            get() = data.getInt(KEY_OPERATION, 0)
        val contentIds: LongArray
            get() {
                return data.getLongArray(KEY_CONTENT_IDS) ?: longArrayOf()
            }
        val chapterIdsForSplit: LongArray
            get() {
                return data.getLongArray(KEY_CHAPTER_IDS_FOR_SPLIT) ?: longArrayOf()
            }
        val deleteAfterOps: Boolean
            get() = data.getBoolean(KEY_DELETE_AFTER_OPS, false)
        val newTitle: String
            get() = data.getString(KEY_NEW_TITLE) ?: ""
        val useBooksAsChapters: Boolean
            get() = data.getBoolean(KEY_USE_BOOKS_AS_CHAPTERS, false)
    }
}