package me.devsaki.hentoid.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.contants.AttributeTable;
import me.devsaki.hentoid.database.contants.ContentAttributeTable;
import me.devsaki.hentoid.database.contants.ContentTable;
import me.devsaki.hentoid.database.contants.ImageFileTable;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by DevSaki on 10/05/2015.
 */
public class HentoidDB extends SQLiteOpenHelper {

    private static final String TAG = HentoidDB.class.getName();
    private static final Object locker = new Object();
    // All Static variables
    // Database Version
    private static final int DATABASE_VERSION = 1;
    // Database Name
    private static final String DATABASE_NAME = "hentoid.db";
    private static HentoidDB instance;

    public HentoidDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ContentTable.CREATE_TABLE);
        db.execSQL(AttributeTable.CREATE_TABLE);
        db.execSQL(ContentAttributeTable.CREATE_TABLE);
        db.execSQL(ImageFileTable.CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + ContentAttributeTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + AttributeTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ContentTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ImageFileTable.TABLE_NAME);

        // Create tables again
        onCreate(db);
    }

    public void insertContent(Content row) {
        insertContents(new Content[]{row});
    }

    public void insertContents(Content[] rows) {
        synchronized (locker) {
            Log.i(TAG, "insertContents");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            try {
                db = getWritableDatabase();
                statement = db.compileStatement(Content.INSERT_STATEMENT);
                db.beginTransaction();
                for (Content row : rows) {

                    deleteContent(db, row);

                    statement.clearBindings();
                    statement.bindLong(1, row.getId());
                    statement.bindString(2, row.getUniqueSiteId());
                    String category = row.getCategory();
                    if (category == null)
                        statement.bindNull(3);
                    else
                        statement.bindString(3, category);
                    statement.bindString(4, row.getUrl());
                    if (row.getHtmlDescription() == null)
                        statement.bindNull(5);
                    else
                        statement.bindString(5, row.getHtmlDescription());
                    if (row.getTitle() == null)
                        statement.bindNull(6);
                    else
                        statement.bindString(6, row.getTitle());
                    statement.bindLong(7, row.getQtyPages());
                    statement.bindLong(8, row.getUploadDate());
                    statement.bindLong(9, row.getDownloadDate());
                    statement.bindLong(10, row.getStatus().getCode());
                    if (row.getCoverImageUrl() == null)
                        statement.bindNull(11);
                    else
                        statement.bindString(11, row.getCoverImageUrl());
                    statement.bindLong(12, row.getSite().getCode());
                    statement.execute();

                    if (row.getImageFiles() != null)
                        insertImageFiles(db, row);

                    List<Attribute> attributes = new ArrayList<>();
                    for (AttributeType attributeType : AttributeType.values()) {
                        if (row.getAttributes().get(attributeType) != null) {
                            attributes.addAll(row.getAttributes().get(attributeType));
                        }
                    }
                    insertAttributes(db, row, attributes);
                }
                db.setTransactionSuccessful();
                db.endTransaction();

            } finally {
                Log.i(TAG, "insertContents - trying to close the db connection. Condition : "
                        + (db != null && db.isOpen()));
                if (statement != null)
                    statement.close();
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
    }

    public void insertImageFiles(Content content) {
        synchronized (locker) {
            Log.i(TAG, "insertImageFiles");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            SQLiteStatement statementImages = null;
            try {
                db = getWritableDatabase();
                db.beginTransaction();
                statement = db.compileStatement(ImageFileTable.INSERT_STATEMENT);
                statementImages = db.compileStatement(ImageFileTable.DELETE_STATEMENT);
                statementImages.clearBindings();
                statementImages.bindLong(1, content.getId());
                statementImages.execute();
                for (ImageFile row : content.getImageFiles()) {
                    statement.clearBindings();
                    statement.bindLong(1, row.getId());
                    statement.bindLong(2, content.getId());
                    statement.bindLong(3, row.getOrder());
                    statement.bindString(4, row.getUrl());
                    statement.bindString(5, row.getName());
                    statement.bindLong(6, row.getStatus().getCode());
                    statement.execute();
                }
                db.setTransactionSuccessful();
                db.endTransaction();

            } finally {
                Log.i(TAG, "insertImageFiles - trying to close the db connection. Condition : "
                        + (db != null && db.isOpen()));
                if (statement != null)
                    statement.close();
                if (statementImages != null)
                    statementImages.close();
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
    }

    private void insertAttributes(SQLiteDatabase db, Content content, List<Attribute> rows) {
        SQLiteStatement statement = null;
        SQLiteStatement statementContentAttribute = null;
        try {
            statement = db.compileStatement(Attribute.INSERT_STATEMENT);
            statementContentAttribute = db.compileStatement(ContentAttributeTable.INSERT_STATEMENT);

            for (Attribute row : rows) {
                statement.clearBindings();
                statement.bindLong(1, row.getId());
                statement.bindString(2, row.getUrl());
                statement.bindString(3, row.getName());
                statement.bindLong(4, row.getType().getCode());
                statement.execute();

                statementContentAttribute.clearBindings();
                statementContentAttribute.bindLong(1, content.getId());
                statementContentAttribute.bindLong(2, row.getId());
                statementContentAttribute.execute();
            }
        } finally {
            if (statement != null)
                statement.close();
            if (statementContentAttribute != null)
                statementContentAttribute.close();
        }
    }

    private void insertImageFiles(SQLiteDatabase db, Content content) {
        SQLiteStatement statement = null;
        try {
            statement = db.compileStatement(ImageFileTable.INSERT_STATEMENT);

            for (ImageFile row : content.getImageFiles()) {
                statement.clearBindings();
                statement.bindLong(1, row.getId());
                statement.bindLong(2, content.getId());
                statement.bindLong(3, row.getOrder());
                statement.bindString(4, row.getUrl());
                statement.bindString(5, row.getName());
                statement.bindLong(6, row.getStatus().getCode());
                statement.execute();
            }
        } finally {
            if (statement != null)
                statement.close();
        }
    }

    public Content selectContentById(int id) {
        Content result = null;
        synchronized (locker) {
            Log.i(TAG, "selectContentById");
            SQLiteDatabase db = null;
            Cursor cursorContents = null;
            try {

                db = getReadableDatabase();
                cursorContents = db.rawQuery(ContentTable.SELECT_BY_CONTENT_ID,
                        new String[]{id + ""});

                // looping through all rows and adding to list
                if (cursorContents.moveToFirst()) {
                    result = populateContent(cursorContents, db);
                }
            } finally {
                if (cursorContents != null) {
                    cursorContents.close();
                }
                Log.i(TAG, "selectContentById - trying to close the db connection. Condition : "
                        + (db != null && db.isOpen()));
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
        return result;
    }

    public Content selectContentByStatus(StatusContent statusContent) {
        Content result = null;

        synchronized (locker) {
            Log.i(TAG, "selectContentByStatus");

            SQLiteDatabase db = null;
            Cursor cursorContent = null;
            try {

                db = getReadableDatabase();
                cursorContent = db.rawQuery(ContentTable.SELECT_BY_STATUS,
                        new String[]{statusContent.getCode() + ""});

                if (cursorContent.moveToFirst()) {
                    result = populateContent(cursorContent, db);
                }
            } finally {
                if (cursorContent != null) {
                    cursorContent.close();
                }
                Log.i(TAG, "selectContentByStatus - trying to close the db connection. Condition : "
                        + (db != null && db.isOpen()));
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }


        return result;
    }

    public List<Content> selectContentInDownloadManager() {
        List<Content> result = null;
        synchronized (locker) {
            Log.i(TAG, "selectContentInDownloadManager");
            SQLiteDatabase db = null;
            Cursor cursorContent = null;
            try {

                db = getReadableDatabase();
                cursorContent = db.rawQuery(ContentTable.SELECT_IN_DOWNLOAD_MANAGER,
                        new String[]{StatusContent.DOWNLOADING.getCode() + "",
                                StatusContent.PAUSED.getCode() + ""});

                if (cursorContent.moveToFirst()) {
                    result = new ArrayList<>();
                    do {
                        result.add(populateContent(cursorContent, db));
                    } while (cursorContent.moveToNext());
                }
            } finally {
                if (cursorContent != null) {
                    cursorContent.close();
                }
                Log.i(TAG,
                        "selectContentInDownloadManager - trying to close the db connection. Condition : "
                                + (db != null && db.isOpen()));
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }

        return result;
    }

    public List<Content> selectContentByQuery(String query, int page, int qty, boolean orderAlphabetic) {
        List<Content> result = null;

        synchronized (locker) {
            Log.i(TAG, "selectContentByQuery");

            SQLiteDatabase db = null;
            Cursor cursorContent = null;
            int start = (page - 1) * qty;
            try {
                query = "%" + query + "%";
                db = getReadableDatabase();
                String sql = ContentTable.SELECT_DOWNLOADS;
                if (orderAlphabetic) {
                    sql += ContentTable.ORDER_ALPHABETIC;
                } else {
                    sql += ContentTable.ORDER_BY_DATE;
                }
                if (qty < 0) {
                    cursorContent = db.rawQuery(sql,
                            new String[]{StatusContent.DOWNLOADED.getCode() + "",
                                    StatusContent.ERROR.getCode() + "",
                                    StatusContent.MIGRATED.getCode() + "", query, query,
                                    AttributeType.ARTIST.getCode() + "",
                                    AttributeType.TAG.getCode() + "",
                                    AttributeType.SERIE.getCode() + ""});
                } else {
                    cursorContent = db.rawQuery(sql + ContentTable.LIMIT_BY_PAGE,
                            new String[]{StatusContent.DOWNLOADED.getCode() + "",
                                    StatusContent.ERROR.getCode() + "",
                                    StatusContent.MIGRATED.getCode() + "", query, query,
                                    AttributeType.ARTIST.getCode() + "",
                                    AttributeType.TAG.getCode() + "",
                                    AttributeType.SERIE.getCode() + "", start + "", qty + ""});
                }


                if (cursorContent.moveToFirst()) {
                    result = new ArrayList<>();
                    do {
                        result.add(populateContent(cursorContent, db));
                    } while (cursorContent.moveToNext());
                }
            } finally {
                if (cursorContent != null) {
                    cursorContent.close();
                }
                Log.i(TAG,
                        "selectContentByQuery - trying to close the db connection. Condition : "
                                + (db != null && db.isOpen()));
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }

        return result;
    }

    private Content populateContent(Cursor cursorContent, SQLiteDatabase db) {
        Content content = new Content();
        content.setUrl(cursorContent.getString(3));
        content.setTitle(cursorContent.getString(4));
        content.setHtmlDescription(cursorContent.getString(5));
        content.setQtyPages(cursorContent.getInt(6));
        content.setUploadDate(cursorContent.getLong(7));
        content.setDownloadDate(cursorContent.getLong(8));
        content.setStatus(StatusContent.searchByCode(cursorContent.getInt(9)));
        content.setCoverImageUrl(cursorContent.getString(10));
        content.setSite(Site.searchByCode(cursorContent.getInt(11)));
        content.setImageFiles(selectImageFilesByContentId(db, content.getId()));
        content.setAttributes(selectAttributesByContentId(db, content.getId()));
        return content;
    }

    private List<ImageFile> selectImageFilesByContentId(SQLiteDatabase db, int id) {
        List<ImageFile> result = null;
        Cursor cursorImageFiles = null;
        try {
            cursorImageFiles = db.rawQuery(ImageFileTable.SELECT_BY_CONTENT_ID,
                    new String[]{id + ""});

            // looping through all rows and adding to list
            if (cursorImageFiles.moveToFirst()) {
                result = new ArrayList<>();
                do {
                    ImageFile item = new ImageFile()
                            .setOrder(cursorImageFiles.getInt(2))
                            .setStatus(StatusContent.searchByCode(cursorImageFiles.getInt(3)))
                            .setUrl(cursorImageFiles.getString(4))
                            .setName(cursorImageFiles.getString(5));
                    result.add(item);
                } while (cursorImageFiles.moveToNext());
            }
        } finally {
            if (cursorImageFiles != null) {
                cursorImageFiles.close();
            }
        }

        return result;
    }

    private AttributeMap selectAttributesByContentId(SQLiteDatabase db, int id) {
        AttributeMap result = null;
        Cursor cursorAttributes = null;
        try {
            cursorAttributes = db.rawQuery(AttributeTable.SELECT_BY_CONTENT_ID,
                    new String[]{id + ""});

            // looping through all rows and adding to list
            if (cursorAttributes.moveToFirst()) {
                result = new AttributeMap();
                do {
                    Attribute item = new Attribute();
                    item.setUrl(cursorAttributes.getString(1));
                    item.setName(cursorAttributes.getString(2));
                    item.setType(AttributeType.searchByCode(cursorAttributes.getInt(3)));
                    result.add(item);
                } while (cursorAttributes.moveToNext());
            }
        } finally {
            if (cursorAttributes != null) {
                cursorAttributes.close();
            }
        }

        return result;
    }

    public void updateImageFileStatus(ImageFile row) {
        synchronized (locker) {
            Log.i(TAG, "updateImageFileStatus");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ImageFileTable.UPDATE_IMAGE_FILE_STATUS_STATEMENT);
                db.beginTransaction();
                statement.clearBindings();
                statement.bindLong(1, row.getStatus().getCode());
                statement.bindLong(2, row.getId());
                statement.execute();
                db.setTransactionSuccessful();
                db.endTransaction();
            } finally {
                Log.i(TAG,
                        "updateImageFileStatus - trying to close the db connection. Condition : "
                                + (db != null && db.isOpen()));
                if (statement != null)
                    statement.close();
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
    }

    private void deleteContent(SQLiteDatabase db, Content content) {
        SQLiteStatement statement = null;
        SQLiteStatement statementImages = null;
        SQLiteStatement statementAttributes = null;
        try {
            statement = db.compileStatement(ContentTable.DELETE_STATEMENT);
            statementImages = db.compileStatement(ImageFileTable.DELETE_STATEMENT);
            statementAttributes = db.compileStatement(ContentAttributeTable.DELETE_STATEMENT);
            statement.clearBindings();
            statement.bindLong(1, content.getId());
            statement.execute();
            statementImages.clearBindings();
            statementImages.bindLong(1, content.getId());
            statementImages.execute();
            statementAttributes.clearBindings();
            statementAttributes.bindLong(1, content.getId());
            statementAttributes.execute();
        } finally {
            if (statement != null)
                statement.close();
            if (statementImages != null)
                statementImages.close();
            if (statementAttributes != null)
                statementAttributes.close();
        }
    }

    public void deleteContent(Content content) {
        synchronized (locker) {
            Log.i(TAG, "deleteContent");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            SQLiteStatement statementImages = null;
            SQLiteStatement statementAttributes = null;
            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable.DELETE_STATEMENT);
                statementImages = db.compileStatement(ImageFileTable.DELETE_STATEMENT);
                statementAttributes = db.compileStatement(ContentAttributeTable.DELETE_STATEMENT);
                db.beginTransaction();
                statement.clearBindings();
                statement.bindLong(1, content.getId());
                statement.execute();
                statementImages.clearBindings();
                statementImages.bindLong(1, content.getId());
                statementImages.execute();
                statementAttributes.clearBindings();
                statementAttributes.bindLong(1, content.getId());
                statementAttributes.execute();
                db.setTransactionSuccessful();
                db.endTransaction();
            } finally {
                Log.i(TAG, "deleteContent - trying to close the db connection. Condition : "
                        + (db != null && db.isOpen()));
                if (statement != null)
                    statement.close();
                if (statementImages != null)
                    statementImages.close();
                if (statementAttributes != null)
                    statementAttributes.close();
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
    }

    public void updateContentStatus(Content row) {
        synchronized (locker) {
            Log.i(TAG, "updateContentStatus");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable
                        .UPDATE_CONTENT_DOWNLOAD_DATE_STATUS_STATEMENT);
                db.beginTransaction();
                statement.clearBindings();
                statement.bindLong(1, row.getDownloadDate());
                statement.bindLong(2, row.getStatus().getCode());
                statement.bindLong(3, row.getId());
                statement.execute();
                db.setTransactionSuccessful();
                db.endTransaction();
            } finally {
                Log.i(TAG,
                        "updateContentStatus - trying to close the db connection. Condition : "
                                + (db != null && db.isOpen()));
                if (statement != null)
                    statement.close();
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
    }

    public void updateContentStatus(StatusContent updateTo, StatusContent updateFrom) {
        synchronized (locker) {
            Log.i(TAG, "updateContentStatus2");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable.UPDATE_CONTENT_STATUS_STATEMENT);
                db.beginTransaction();
                statement.clearBindings();
                statement.bindLong(1, updateTo.getCode());
                statement.bindLong(2, updateFrom.getCode());
                statement.execute();
                db.setTransactionSuccessful();
                db.endTransaction();
            } finally {
                Log.i(TAG,
                        "updateContentStatus2 - trying to close the db connection. Condition : "
                                + (db != null && db.isOpen()));
                if (statement != null)
                    statement.close();
                if (db != null && db.isOpen())
                    db.close(); // Closing database connection
            }
        }
    }
}