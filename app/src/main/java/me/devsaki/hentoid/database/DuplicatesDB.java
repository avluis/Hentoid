package me.devsaki.hentoid.database;

import android.content.Context;

import java.util.List;

import io.objectbox.BoxStore;
import io.objectbox.android.AndroidObjectBrowser;
import io.objectbox.query.Query;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.database.domains.DuplicateEntry;
import me.devsaki.hentoid.database.domains.DuplicateEntry_;
import me.devsaki.hentoid.database.domains.MyObjectBox;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

public class DuplicatesDB {

    //private static final File DB_DIRECTORY = new File("duplicates-db");
    private static final String DB_NAME = "duplicates-db";

    private static DuplicatesDB instance;

    private final BoxStore store;


    private DuplicatesDB(Context context) {
        store = MyObjectBox.builder().name(DB_NAME).androidContext(context.getApplicationContext()).maxSizeInKByte(Preferences.getMaxDbSizeKb()).build();

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
            store.deleteAllFiles();
        }
    }

    public Query<DuplicateEntry> selectEntriesQ() {
        return store.boxFor(DuplicateEntry.class).query().orderDesc(DuplicateEntry_.referenceSize).build();
    }

    void insertEntry(DuplicateEntry entry) {
        store.boxFor(DuplicateEntry.class).put(entry);
    }

    void insertEntries(List<DuplicateEntry> entry) {
        store.boxFor(DuplicateEntry.class).put(entry);
    }

    void clearEntries() {
        store.boxFor(DuplicateEntry.class).removeAll();
    }
}
