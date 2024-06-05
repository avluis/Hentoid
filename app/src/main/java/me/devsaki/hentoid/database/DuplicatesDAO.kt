package me.devsaki.hentoid.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.objectbox.android.ObjectBoxLiveData
import me.devsaki.hentoid.database.domains.DuplicateEntry

class DuplicatesDAO {
    fun cleanup() {
        ObjectBoxDB.cleanup()
        DuplicatesDB.cleanup()
    }

    fun getEntries(): List<DuplicateEntry> {
        val entries = DuplicatesDB.selectEntriesQ().find()

        // Get all contents in one go
        val contentIds = entries.map { it.referenceId }
        val contents = ObjectBoxDB.selectContentById(contentIds)
        val contentsMap = contents.groupBy { it.id }

        // Map them back to the corresponding entry
        for (entry in entries) {
            entry.referenceContent = contentsMap[entry.referenceId]?.get(0)
            entry.duplicateContent = contentsMap[entry.duplicateId]?.get(0)
        }
        return entries
    }

    fun getEntriesLive(): LiveData<List<DuplicateEntry>> {
        val livedata = ObjectBoxLiveData(DuplicatesDB.selectEntriesQ())

        // Get all contents in one go
        val livedata2 = MediatorLiveData<List<DuplicateEntry>>()
        livedata2.addSource(livedata) { it ->
            val enrichedItems = it.map { enrichWithContent(it) }
            livedata2.value = enrichedItems
        }

        return livedata2
    }

    private fun enrichWithContent(e: DuplicateEntry): DuplicateEntry {
        val items = ObjectBoxDB.selectContentById(mutableListOf(e.referenceId, e.duplicateId))
        if (items.size > 1) {
            e.referenceContent = items[0]
            e.duplicateContent = items[1]
        }
        return e
    }

    fun clearEntries() {
        DuplicatesDB.clearEntries()
    }

    fun insertEntries(entry: List<DuplicateEntry>) {
        DuplicatesDB.insertEntries(entry)
    }

    fun delete(entry: DuplicateEntry) {
        DuplicatesDB.delete(entry)
    }
}