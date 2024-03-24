package me.devsaki.hentoid.database

import io.objectbox.BoxStore
import io.objectbox.query.Query
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.database.domains.DuplicateEntry_
import me.devsaki.hentoid.database.domains.MyObjectBox
import me.devsaki.hentoid.util.Preferences

object DuplicatesDB {
    private const val DB_NAME = "duplicates-db"

    val store: BoxStore by lazy { initStore() }

    private fun initStore(): BoxStore {
        val context = HentoidApp.getInstance()
        return MyObjectBox.builder().name(DB_NAME).androidContext(context)
            .maxSizeInKByte(Preferences.getMaxDbSizeKb()).build()
    }


    fun cleanup() {
        store.closeThreadResources()
    }

    fun tearDown() {
        store.closeThreadResources()
        store.close()
        store.deleteAllFiles()
    }

    fun selectEntriesQ(): Query<DuplicateEntry> {
        return store.boxFor(DuplicateEntry::class.java).query()
            .orderDesc(DuplicateEntry_.referenceSize).build()
    }

    fun insertEntries(entry: List<DuplicateEntry>) {
        store.boxFor(DuplicateEntry::class.java).put(entry)
    }

    fun delete(entry: DuplicateEntry) {
        store.boxFor(DuplicateEntry::class.java).remove(entry)
    }

    fun clearEntries() {
        store.boxFor(DuplicateEntry::class.java).removeAll()
    }
}