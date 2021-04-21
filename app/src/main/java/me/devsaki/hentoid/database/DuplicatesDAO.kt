package me.devsaki.hentoid.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.objectbox.android.ObjectBoxLiveData
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry

class DuplicatesDAO(ctx: Context) {
    private val duplicatesDb: DuplicatesDB = DuplicatesDB.getInstance(ctx)
    private val db: ObjectBoxDB = ObjectBoxDB.getInstance(ctx)

    fun cleanup() {
        db.closeThreadResources()
        duplicatesDb.closeThreadResources()
    }

    fun getDbSizeBytes(): Long {
        return duplicatesDb.dbSizeBytes
    }

    fun getEntries(): List<DuplicateEntry> {
        val entries = duplicatesDb.selectEntriesQ().find()

        // Get all contents in one go
        val contentIds = entries.map { it.reference }
        val contents = db.selectContentById(contentIds)
        if (contents != null) {
            val contentsMap = contents.groupBy { it.id }

            // Map them back to the corresponding entry
            for (entry in entries) {
                entry.referenceContent = contentsMap[entry.reference]?.get(0)
                entry.duplicateContent = contentsMap[entry.duplicate]?.get(0)
            }
        }
        return entries
    }

    fun getEntriesLive(): LiveData<List<DuplicateEntry>> {
        val livedata = ObjectBoxLiveData(duplicatesDb.selectEntriesQ())

        // Get all contents in one go
        val livedata2 = MediatorLiveData<List<DuplicateEntry>>()
        livedata2.addSource(livedata) { it ->
            val enrichedItems = it.map { enrichWithContent(it) }
            livedata2.value = enrichedItems
        }

        return livedata2
    }

    private fun enrichWithContent(e: DuplicateEntry): DuplicateEntry {
        val items: List<Content>? = db.selectContentById(mutableListOf(e.reference, e.duplicate))
        if (items != null && items.size > 1) {
            e.referenceContent = items[0]
            e.duplicateContent = items[1]
        }
        return e
    }

    fun clearEntries() {
        duplicatesDb.clearEntries()
    }

    fun insertEntry(entry: DuplicateEntry) {
        duplicatesDb.insertEntry(entry)
    }

    fun insertEntries(entry: List<DuplicateEntry>) {
        duplicatesDb.insertEntries(entry)
    }
}