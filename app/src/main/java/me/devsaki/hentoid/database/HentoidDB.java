package me.devsaki.hentoid.database;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Pair;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.constants.AttributeTable;
import me.devsaki.hentoid.database.constants.ContentAttributeTable;
import me.devsaki.hentoid.database.constants.ContentTable;
import me.devsaki.hentoid.database.constants.ImageFileTable;
import me.devsaki.hentoid.database.constants.QueueTable;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Consts;
import timber.log.Timber;

/**
 * Created by DevSaki on 10/05/2015.
 * db maintenance class
 */
public class HentoidDB extends SQLiteOpenHelper {

    private static final Object locker = new Object();
    private static final int DATABASE_VERSION = 7;
    private static HentoidDB instance;


    private HentoidDB(Context context) {
        super(context, Consts.DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Use this to get db instance
    public static synchronized HentoidDB getInstance(Context context) {
        // Use application context only
        if (instance == null) {
            instance = new HentoidDB(context.getApplicationContext());
        }

        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ContentTable.CREATE_TABLE);
        db.execSQL(AttributeTable.CREATE_TABLE);
        db.execSQL(ContentAttributeTable.CREATE_TABLE);
        db.execSQL(ImageFileTable.CREATE_TABLE);
        db.execSQL(ImageFileTable.SELECT_PROCESSED_BY_CONTENT_ID_IDX);
        db.execSQL(QueueTable.CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.AUTHOR_COLUMN + " TEXT");
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.STORAGE_FOLDER_COLUMN + " TEXT");
            Timber.i("Upgrading DB version to v2");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.FAVOURITE_COLUMN + " INTEGER DEFAULT 0");
            Timber.i("Upgrading DB version to v3");
        }
        if (oldVersion < 4) {
            db.execSQL(QueueTable.CREATE_TABLE);
            Timber.i("Upgrading DB version to v4");
        }
        if (oldVersion < 5) {
            db.execSQL(ImageFileTable.SELECT_PROCESSED_BY_CONTENT_ID_IDX);
            Timber.i("Upgrading DB version to v5");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.READS_COLUMN + " INTEGER DEFAULT 1");
            Timber.i("Upgrading DB version to v6");
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.LAST_READ_DATE_COLUMN + " INTEGER");
            db.execSQL("UPDATE " + ContentTable.TABLE_NAME + " SET " + ContentTable.LAST_READ_DATE_COLUMN + " = " + ContentTable.DOWNLOAD_DATE_COLUMN);
            Timber.i("Upgrading DB version to v7");
        }
    }

    long countContentEntries() {
        long count;

        SQLiteDatabase db = null;
        try {
            db = getReadableDatabase();
            count = DatabaseUtils.queryNumEntries(db, ContentTable.TABLE_NAME);
        } finally {
            if (db != null && db.isOpen()) {
                db.close(); // Closing database connection
            }
        }

        return count;
    }

    @Nullable
    public Content selectContentById(long id) {
        Content result;
        synchronized (locker) {
            Timber.d("selectContentById");
            SQLiteDatabase db = null;
            try {
                db = getReadableDatabase();
                result = selectContentById(db, id);
            } finally {
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }

    @Nullable
    private Content selectContentById(SQLiteDatabase db, long id) {
        Content result = null;

        try (Cursor cursorContents = db.rawQuery(ContentTable.SELECT_BY_CONTENT_ID, new String[]{id + ""})) {

            if (cursorContents.moveToFirst()) {
                result = populateContent(cursorContents, db);
            }
        }

        return result;
    }

    List<Content> selectContentEmptyFolder() {
        List<Content> result;
        synchronized (locker) {
            Timber.d("selectContentEmptyFolder");
            SQLiteDatabase db = null;
            Cursor cursorContent = null;
            try {
                db = getReadableDatabase();
                cursorContent = db.rawQuery(ContentTable.SELECT_NULL_FOLDERS, new String[]{});
                result = populateResult(cursorContent, db);
            } finally {
                closeCursor(cursorContent, db);
            }
        }

        return result;
    }

    private List<Content> populateResult(Cursor cursorContent, SQLiteDatabase db) {
        List<Content> result = Collections.emptyList();
        if (cursorContent.moveToFirst()) {
            result = new ArrayList<>();
            do {
                result.add(populateContent(cursorContent, db));
            } while (cursorContent.moveToNext());
        }

        return result;
    }

    private void closeCursor(Cursor cursorContent, SQLiteDatabase db) {
        if (cursorContent != null) {
            cursorContent.close();
        }
        Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
        if (db != null && db.isOpen()) {
            db.close(); // Closing database connection
        }
    }

    private Content populateContent(Cursor cursorContent, SQLiteDatabase db) {
        Content content = new Content()
                .setSite(Site.searchByCode(cursorContent.getInt(ContentTable.IDX_SOURCECODE - 1)))
                .setUrl(cursorContent.getString(ContentTable.IDX_URL - 1))
                .setTitle(cursorContent.getString(ContentTable.IDX_TITLE - 1))
                .setQtyPages(cursorContent.getInt(ContentTable.IDX_QTYPAGES - 1))
                .setUploadDate(cursorContent.getLong(ContentTable.IDX_ULDATE - 1))
                .setDownloadDate(cursorContent.getLong(ContentTable.IDX_DLDATE - 1))
                .setStatus(StatusContent.searchByCode(cursorContent.getInt(ContentTable.IDX_STATUSCODE - 1)))
                .setCoverImageUrl(cursorContent.getString(ContentTable.IDX_COVERURL - 1))
                .setAuthor(cursorContent.getString(ContentTable.IDX_AUTHOR - 1))
                .setStorageFolder(cursorContent.getString(ContentTable.IDX_STORAGE_FOLDER - 1))
                .setFavourite(1 == cursorContent.getInt(ContentTable.IDX_FAVOURITE - 1))
                .setReads(cursorContent.getLong(ContentTable.IDX_READS - 1))
                .setLastReadDate(cursorContent.getLong(ContentTable.IDX_LAST_READ_DATE - 1))
                .setQueryOrder(cursorContent.getPosition());

        long id = cursorContent.getLong(ContentTable.IDX_INTERNALID - 1);

        content.addImageFiles(selectImageFilesByContentId(db, id))
                .addAttributes(selectAttributesByContentId(db, id));

        content.populateAuthor();

        return content;
    }

    private List<ImageFile> selectImageFilesByContentId(SQLiteDatabase db, long id) {
        List<ImageFile> result = Collections.emptyList();
        try (Cursor cursorImageFiles = db.rawQuery(ImageFileTable.SELECT_BY_CONTENT_ID,
                new String[]{id + ""})) {

            // looping through all rows and adding to list
            if (cursorImageFiles.moveToFirst()) {
                result = new ArrayList<>();
                do {
                    result.add(new ImageFile()
                            .setOrder(cursorImageFiles.getInt(2))
                            .setStatus(StatusContent.searchByCode(cursorImageFiles.getInt(3)))
                            .setUrl(cursorImageFiles.getString(4))
                            .setName(cursorImageFiles.getString(5)));
                } while (cursorImageFiles.moveToNext());
            }
        }

        return result;
    }

    private AttributeMap selectAttributesByContentId(SQLiteDatabase db, long id) {
        AttributeMap result = null;
        try (Cursor cursorAttributes = db.rawQuery(AttributeTable.SELECT_BY_CONTENT_ID,
                new String[]{id + ""})) {

            // looping through all rows and adding to list
            if (cursorAttributes.moveToFirst()) {
                result = new AttributeMap();
                do {
                    result.add(new Attribute()
                            .setUrl(cursorAttributes.getString(1))
                            .setName(cursorAttributes.getString(2))
                            .setType(AttributeType.searchByCode(cursorAttributes.getInt(3))));
                } while (cursorAttributes.moveToNext());
            }
        }

        return result;
    }

    void updateContentStorageFolder(Content row) {
        synchronized (locker) {
            Timber.d("updateContentStorageFolder");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable.UPDATE_CONTENT_STORAGE_FOLDER);
                db.beginTransaction();
                try {
                    statement.clearBindings();
                    statement.bindString(1, row.getStorageFolder());
                    statement.bindLong(2, row.getId());
                    statement.execute();
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (statement != null) {
                    statement.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }
    }

    void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
        synchronized (locker) {
            Timber.d("updateContentStatus2");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable.UPDATE_CONTENT_STATUS_STATEMENT);
                db.beginTransaction();
                try {
                    statement.clearBindings();
                    statement.bindLong(1, updateTo.getCode());
                    statement.bindLong(2, updateFrom.getCode());
                    statement.execute();
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (statement != null) {
                    statement.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }
    }

    List<Pair<Integer, Integer>> selectQueue() {
        ArrayList<Pair<Integer, Integer>> result = new ArrayList<>();

        synchronized (locker) {
            Timber.d("selectQueue");
            SQLiteDatabase db = null;
            Cursor cursorQueue = null;

            try {
                db = getReadableDatabase();
                cursorQueue = db.rawQuery(QueueTable.SELECT_QUEUE, new String[]{});

                // looping through all rows and adding to list
                if (cursorQueue.moveToFirst()) {
                    do {
                        result.add(new Pair<>(cursorQueue.getInt(0), cursorQueue.getInt(1)));
                    } while (cursorQueue.moveToNext());
                }
            } finally {
                if (cursorQueue != null) {
                    cursorQueue.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }

    List<Integer> selectContentsForQueueMigration() {
        ArrayList<Integer> result = new ArrayList<>();

        synchronized (locker) {
            Timber.d("selectContentsForQueueMigration");
            SQLiteDatabase db = null;
            Cursor cursorQueue = null;

            try {
                db = getReadableDatabase();
                cursorQueue = db.rawQuery(QueueTable.SELECT_CONTENT_FOR_QUEUE_MIGRATION, new String[]{});

                // looping through all rows and adding to list
                if (cursorQueue.moveToFirst()) {
                    do {
                        result.add(cursorQueue.getInt(0));
                    } while (cursorQueue.moveToNext());
                }
            } finally {
                if (cursorQueue != null) {
                    cursorQueue.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }

    void insertQueue(long id, int order) {
        synchronized (locker) {
            Timber.d("insertQueue");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();

                statement = db.compileStatement(QueueTable.INSERT_STATEMENT);
                statement.clearBindings();

                statement.bindLong(1, id);
                statement.bindLong(2, order);
                statement.execute();
            } finally {
                if (statement != null) {
                    statement.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }
    }

    public List<Integer> selectMigrableContentIds() {
        ArrayList<Integer> result = new ArrayList<>();

        Timber.d("selectMigrableContentIds");
        SQLiteDatabase db = null;
        Cursor cursorQueue = null;

        try {
            db = getReadableDatabase();
            cursorQueue = db.rawQuery(ContentTable.SELECT_MIGRABLE_CONTENT, new String[]{
                    StatusContent.DOWNLOADED.getCode() + "",
                    StatusContent.ERROR.getCode() + "",
                    StatusContent.MIGRATED.getCode() + "",
                    StatusContent.DOWNLOADING.getCode() + "",
                    StatusContent.PAUSED.getCode() + ""
            });

            // looping through all rows and adding to list
            if (cursorQueue.moveToFirst()) {
                do {
                    result.add(cursorQueue.getInt(0));
                } while (cursorQueue.moveToNext());
            }
        } finally {
            if (cursorQueue != null) {
                cursorQueue.close();
            }
            if (db != null && db.isOpen()) {
                db.close(); // Closing database connection
            }
        }

        return result;
    }

    public SparseIntArray selectQueueForMigration() {
        SparseIntArray result = new SparseIntArray();

        synchronized (locker) {
            Timber.d("selectQueueForMigration");
            SQLiteDatabase db = null;
            Cursor cursorQueue = null;

            try {
                db = getReadableDatabase();
                cursorQueue = db.rawQuery(QueueTable.SELECT_QUEUE, new String[]{});

                // looping through all rows and adding to list
                if (cursorQueue.moveToFirst()) {
                    do {
                        result.put(cursorQueue.getInt(0), cursorQueue.getInt(1));
                    } while (cursorQueue.moveToNext());
                }
            } finally {
                if (cursorQueue != null) {
                    cursorQueue.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }
}