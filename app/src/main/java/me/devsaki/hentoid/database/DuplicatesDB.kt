package me.devsaki.hentoid.database

import io.objectbox.BoxStore
import io.objectbox.android.Admin
import io.objectbox.query.Query
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.database.domains.DuplicateEntry_
import me.devsaki.hentoid.database.domains.MyObjectBox
import me.devsaki.hentoid.util.Preferences
import timber.log.Timber

object DuplicatesDB {
    private const val DB_NAME = "duplicates-db"

    val store: BoxStore by lazy { initStore() }

    private fun initStore(): BoxStore {
        val context = HentoidApp.getInstance()
        val mStore = MyObjectBox.builder().name(DB_NAME).androidContext(context)
            .maxSizeInKByte(Preferences.getMaxDbSizeKb()).build()
        if (BuildConfig.DEBUG && BuildConfig.INCLUDE_OBJECTBOX_BROWSER) {
            val started = Admin(mStore).start(context)
            Timber.i("ObjectBrowser started: %s", started)
        }
        return mStore
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