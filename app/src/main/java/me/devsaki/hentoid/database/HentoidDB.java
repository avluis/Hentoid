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
 *
 * @deprecated Replaced by {@link ObjectBoxDB}; class is kept for data migration purposes
 */
@Deprecated
@SuppressWarnings("squid:S1192") // Putting SQL literals into constants would be too cumbersome
public class HentoidDB extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 8;
    private static HentoidDB instance;

    private SQLiteDatabase mDatabase;
    private int mOpenCounter;


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
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.DOWNLOAD_PARAMS_COLUMN + " TEXT");
            db.execSQL("UPDATE " + ContentTable.TABLE_NAME + " SET " + ContentTable.DOWNLOAD_PARAMS_COLUMN + " = ''");
            db.execSQL("ALTER TABLE " + ImageFileTable.TABLE_NAME + " ADD COLUMN " + ImageFileTable.DOWNLOAD_PARAMS_COLUMN + " TEXT");
            db.execSQL("UPDATE " + ImageFileTable.TABLE_NAME + " SET " + ImageFileTable.DOWNLOAD_PARAMS_COLUMN + " = ''");
            Timber.i("Upgrading DB version to v8");
        }
    }

    // The two following methods to handle multiple threads accessing the DB simultaneously
    // => only the last active thread will close the DB
    private synchronized SQLiteDatabase openDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            Timber.d("Opening db connection.");
            mDatabase = this.getWritableDatabase();
        }
        return mDatabase;
    }

    private synchronized void closeDatabase() {
        mOpenCounter--;
        if (0 == mOpenCounter && mDatabase != null && mDatabase.isOpen()) {
            Timber.d("Closing db connection.");
            mDatabase.close();
        }
    }


    // FUNCTIONAL METHODS

    long countContentEntries() {
        long count;

        SQLiteDatabase db = openDatabase();
        try {
            count = DatabaseUtils.queryNumEntries(db, ContentTable.TABLE_NAME);
        } finally {
            closeDatabase();
        }

        return count;
    }

    @Nullable
    public Content selectContentById(long id) {
        Content result;
        Timber.d("selectContentById");
        SQLiteDatabase db = openDatabase();
        try {
            result = selectContentById(db, id);
        } finally {
            closeDatabase();
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
        Timber.d("selectContentEmptyFolder");
        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(ContentTable.SELECT_NULL_FOLDERS, new String[]{})) {
            result = populateResult(cursorContent, db);
        } finally {
            closeDatabase();
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
                .setDownloadParams(cursorContent.getString(ContentTable.IDX_DOWNLOAD_PARAMS - 1))
                .setQueryOrder(cursorContent.getPosition());

        long id = cursorContent.getLong(ContentTable.IDX_INTERNALID - 1);

        content.addImageFiles(selectImageFilesByContentId(db, id))
                .addAttributes(selectAttributesByContentId(db, id, content.getSite()));

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
                            .setName(cursorImageFiles.getString(5))
                            .setDownloadParams(cursorImageFiles.getString(6))
                    );
                } while (cursorImageFiles.moveToNext());
            }
        }

        return result;
    }

    private AttributeMap selectAttributesByContentId(SQLiteDatabase db, long id, Site site) {
        AttributeMap result = null;
        try (Cursor cursorAttributes = db.rawQuery(AttributeTable.SELECT_BY_CONTENT_ID,
                new String[]{id + ""})) {

            // looping through all rows and adding to list
            if (cursorAttributes.moveToFirst()) {
                result = new AttributeMap();
                do {
                    result.add(
                            new Attribute(
                                    AttributeType.searchByCode(cursorAttributes.getInt(3)),
                                    cursorAttributes.getString(2),
                                    cursorAttributes.getString(1),
                                    site
                            )
                    );
                } while (cursorAttributes.moveToNext());
            }
        }

        return result;
    }

    void updateContentStorageFolder(Content row) {
        Timber.d("updateContentStorageFolder");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ContentTable.UPDATE_CONTENT_STORAGE_FOLDER)) {
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
            closeDatabase();
        }
    }


    void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
        Timber.d("updateContentStatus2");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ContentTable.UPDATE_CONTENT_STATUS_STATEMENT)) {
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
            closeDatabase();
        }
    }

    List<Pair<Integer, Integer>> selectQueue() {
        ArrayList<Pair<Integer, Integer>> result = new ArrayList<>();

        Timber.d("selectQueue");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorQueue = db.rawQuery(QueueTable.SELECT_QUEUE, new String[]{})) {

            // looping through all rows and adding to list
            if (cursorQueue.moveToFirst()) {
                do {
                    result.add(new Pair<>(cursorQueue.getInt(0), cursorQueue.getInt(1)));
                } while (cursorQueue.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }


    List<Integer> selectContentsForQueueMigration() {
        ArrayList<Integer> result = new ArrayList<>();

        Timber.d("selectContentsForQueueMigration");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorQueue = db.rawQuery(QueueTable.SELECT_CONTENT_FOR_QUEUE_MIGRATION, new String[]{})) {

            // looping through all rows and adding to list
            if (cursorQueue.moveToFirst()) {
                do {
                    result.add(cursorQueue.getInt(0));
                } while (cursorQueue.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }


    void insertQueue(int id, int order) {
        Timber.d("insertQueue");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(QueueTable.INSERT_STATEMENT)) {
            statement.clearBindings();

            statement.bindLong(1, id);
            statement.bindLong(2, order);
            statement.execute();
        } finally {
            closeDatabase();
        }
    }

    public List<Integer> selectMigrableContentIds() {
        ArrayList<Integer> result = new ArrayList<>();

        Timber.d("selectMigrableContentIds");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorQueue = db.rawQuery(ContentTable.SELECT_MIGRABLE_CONTENT, new String[]{
                StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + "",
                StatusContent.DOWNLOADING.getCode() + "",
                StatusContent.PAUSED.getCode() + ""
        })) {

            // looping through all rows and adding to list
            if (cursorQueue.moveToFirst()) {
                do {
                    result.add(cursorQueue.getInt(0));
                } while (cursorQueue.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    public SparseIntArray selectQueueForMigration() {
        SparseIntArray result = new SparseIntArray();

        Timber.d("selectQueueForMigration");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorQueue = db.rawQuery(QueueTable.SELECT_QUEUE, new String[]{})) {

            // looping through all rows and adding to list
            if (cursorQueue.moveToFirst()) {
                do {
                    result.put(cursorQueue.getInt(0), cursorQueue.getInt(1));
                } while (cursorQueue.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }
}