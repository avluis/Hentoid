package me.devsaki.hentoid.database;

import android.content.Context;

import java.io.File;
import java.util.List;

import io.objectbox.BoxStore;
import io.objectbox.android.AndroidObjectBrowser;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.database.domains.DuplicateEntry;
import me.devsaki.hentoid.database.domains.DuplicateEntry_;
import me.devsaki.hentoid.database.domains.MyObjectBox;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

public class DuplicatesDB {

    private static final File DB_DIRECTORY = new File("duplicates-db");

    private static DuplicatesDB instance;

    private final BoxStore store;


    private DuplicatesDB(Context context) {
        store = MyObjectBox.builder().directory(DB_DIRECTORY).androidContext(context.getApplicationContext()).maxSizeInKByte(Preferences.getMaxDbSizeKb()).build();

        if (BuildConfig.DEBUG && BuildConfig.INCLUDE_OBJECTBOX_BROWSER) {
            boolean started = new AndroidObjectBrowser(store).start(context.getApplicationContext());
            Timber.i("ObjectBrowser started: %s", started);
        }
    }

    // Use this to get db instance
    public static synchronized DuplicatesDB getInstance(Context context) {
        // Use application context only
        if (instance == null) {
            instance = new DuplicatesDB(context);
        }

        return instance;
    }


    void closeThreadResources() {
        store.closeThreadResources();
    }

    long getDbSizeBytes() {
        return store.sizeOnDisk();
    }

    public void tearDown() {
        if (store != null) {
            store.closeThreadResources();
            store.close();
        }
        BoxStore.deleteAllFiles(DB_DIRECTORY);
    }

    public List<DuplicateEntry> getEntries() {
        return store.boxFor(DuplicateEntry.class).query().orderDesc(DuplicateEntry_.referenceSize).build().find();
    }

    void insertEntry(DuplicateEntry entry) {
        store.boxFor(DuplicateEntry.class).put(entry);
    }

    void insertEntries(List<DuplicateEntry> entry) {
        store.boxFor(DuplicateEntry.class).put(entry);
    }
}
