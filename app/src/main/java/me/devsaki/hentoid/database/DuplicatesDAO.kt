package me.devsaki.hentoid.database

import android.content.Context
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
        val entries = duplicatesDb.entries

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

    fun insertEntry(entry : DuplicateEntry) {
        duplicatesDb.insertEntry(entry)
    }

    fun insertEntries(entry : List<DuplicateEntry>) {
        duplicatesDb.insertEntries(entry)
    }
}