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
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import timber.log.Timber;

/**
 * Created by DevSaki on 10/05/2015.
 * db maintenance class
 */
public class HentoidDB extends SQLiteOpenHelper {

    private static final Object locker = new Object();
    private static final int DATABASE_VERSION = 5;
    private static HentoidDB instance;


    // TODO : enable foreign keys & indexes

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

        if (oldVersion < 2) // Updates to v2
        {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.AUTHOR_COLUMN + " TEXT");
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.STORAGE_FOLDER_COLUMN + " TEXT");
            Timber.i("Upgrading DB version to v2");
        }
        if (oldVersion < 3) // Updates to v3
        {
            db.execSQL("ALTER TABLE " + ContentTable.TABLE_NAME + " ADD COLUMN " + ContentTable.FAVOURITE_COLUMN + " INTEGER DEFAULT 0");
            Timber.i("Upgrading DB version to v3");
        }
        if (oldVersion < 4) // Updates to v4
        {
            db.execSQL(QueueTable.CREATE_TABLE);
            Timber.i("Upgrading DB version to v4");
        }
        if (oldVersion < 5) // Updates to v5
        {
            db.execSQL(ImageFileTable.SELECT_PROCESSED_BY_CONTENT_ID_IDX);
            Timber.i("Upgrading DB version to v5");
        }
    }

    public long countContentEntries() {
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

    public void insertContent(Content row) {
        insertContents(new Content[]{row});
    }

    public void insertContents(Content[] rows) {
        synchronized (locker) {
            Timber.d("insertContents");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable.INSERT_STATEMENT);
                db.beginTransaction();

                try {
                    for (Content row : rows) {
                        deleteContent(db, row, false);

                        statement.clearBindings();
                        statement.bindLong(ContentTable.IDX_INTERNALID, row.getId());
                        statement.bindString(ContentTable.IDX_SITEID, row.getUniqueSiteId());
                        String category = row.getCategory();

                        if (category == null) {
                            statement.bindNull(ContentTable.IDX_CATEGORY);
                        } else {
                            statement.bindString(ContentTable.IDX_CATEGORY, category);
                        }

                        statement.bindString(ContentTable.IDX_URL, row.getUrl());

                        if (row.getTitle() == null) {
                            statement.bindNull(ContentTable.IDX_TITLE);
                        } else {
                            statement.bindString(ContentTable.IDX_TITLE, row.getTitle());
                        }

                        statement.bindLong(ContentTable.IDX_QTYPAGES, row.getQtyPages());
                        statement.bindLong(ContentTable.IDX_ULDATE, row.getUploadDate());
                        statement.bindLong(ContentTable.IDX_DLDATE, row.getDownloadDate());
                        statement.bindLong(ContentTable.IDX_STATUSCODE, row.getStatus().getCode());

                        if (row.getCoverImageUrl() == null) {
                            statement.bindNull(ContentTable.IDX_COVERURL);
                        } else {
                            statement.bindString(ContentTable.IDX_COVERURL, row.getCoverImageUrl());
                        }

                        statement.bindLong(ContentTable.IDX_SITECODE, row.getSite().getCode());
                        statement.bindString(ContentTable.IDX_AUTHOR, (null == row.getAuthor()) ? "" : row.getAuthor());
                        statement.bindString(ContentTable.IDX_STORAGE_FOLDER, (null == row.getStorageFolder()) ? "" : row.getStorageFolder());
                        statement.bindLong(ContentTable.IDX_FAVOURITE, row.isFavourite() ? 1 : 0);

                        statement.execute();

                        if (row.getImageFiles() != null) {
                            insertImageFiles(db, row);
                        }

                        List<Attribute> attributes = new ArrayList<>();
                        for (AttributeType attributeType : AttributeType.values()) {
                            if (row.getAttributes().get(attributeType) != null) {
                                attributes.addAll(row.getAttributes().get(attributeType));
                            }
                        }
                        insertAttributes(db, row, attributes);
                    }
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

    public void insertImageFiles(Content content) {
        synchronized (locker) {
            Timber.d("insertImageFiles");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            SQLiteStatement statementImages = null;

            try {
                db = getWritableDatabase();
                try {
                    db.beginTransaction();
                    statement = db.compileStatement(ImageFileTable.INSERT_STATEMENT);
                    statementImages = db.compileStatement(ImageFileTable.DELETE_STATEMENT);
                    statementImages.clearBindings();
                    statementImages.bindLong(1, content.getId());
                    statementImages.execute();

                    insertImageFiles(statement, content);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (statement != null) {
                    statement.close();
                }
                if (statementImages != null) {
                    statementImages.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }
    }

    private void insertAttributes(SQLiteDatabase db, Content content, List<Attribute> rows) {
        SQLiteStatement statement = null;
        SQLiteStatement statementContentAttribute = null;

        try {
            statement = db.compileStatement(AttributeTable.INSERT_STATEMENT);
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
            if (statement != null) {
                statement.close();
            }
            if (statementContentAttribute != null) {
                statementContentAttribute.close();
            }
        }
    }

    private void insertImageFiles(SQLiteDatabase db, Content content) {
        SQLiteStatement statement = null;
        try {
            statement = db.compileStatement(ImageFileTable.INSERT_STATEMENT);
            insertImageFiles(statement, content);
        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }

    private void insertImageFiles(SQLiteStatement statement, Content content) {
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
    }

    @Nullable
    public Content selectContentById(int id) {
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
    private Content selectContentById(SQLiteDatabase db, int id) {
        Cursor cursorContents = null;
        Content result = null;

        try {
            cursorContents = db.rawQuery(ContentTable.SELECT_BY_CONTENT_ID, new String[]{id + ""});

            if (cursorContents.moveToFirst()) {
                result = populateContent(cursorContents, db);
            }
        } finally {
            if (cursorContents != null) {
                cursorContents.close();
            }
        }

        return result;
    }

    public List<Content> selectContentEmptyFolder() {
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

    // This is a long running task, execute with AsyncTask or similar
    public List<Content> selectContentByQuery(String title, int page, int booksPerPage, List<Attribute> tags, boolean filterFavourites, int orderStyle) {
        List<Content> result = Collections.emptyList();

        synchronized (locker) {
            Timber.d("selectContentByQuery");

            SQLiteDatabase db = null;
            Cursor cursorContent = null;
            int start = (page - 1) * booksPerPage;
            try {
                db = getReadableDatabase();
                String sql = buildContentSearchQuery(title, tags, filterFavourites);

                switch (orderStyle) {
                    case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST:
                        sql += ContentTable.ORDER_BY_DATE + " DESC";
                        break;
                    case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST:
                        sql += ContentTable.ORDER_BY_DATE;
                        break;
                    case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC:
                        sql += ContentTable.ORDER_ALPHABETIC;
                        break;
                    case Preferences.Constant.PREF_ORDER_CONTENT_ALPHABETIC_INVERTED:
                        sql += ContentTable.ORDER_ALPHABETIC + " DESC";
                        break;
                    case Preferences.Constant.PREF_ORDER_CONTENT_RANDOM:
                        sql += ContentTable.ORDER_RANDOM.replace("%6", String.valueOf(RandomSeedSingleton.getInstance().getRandomNumber()));
                        break;
                    default:
                        // Nothing
                }

                Timber.d("Query : %s; %s, %s", sql, start, booksPerPage);

                if (booksPerPage < 0) {
                    cursorContent = db.rawQuery(sql,
                            new String[]{StatusContent.DOWNLOADED.getCode() + "",
                                    StatusContent.ERROR.getCode() + "",
                                    StatusContent.MIGRATED.getCode() + ""});
                } else {
                    cursorContent = db.rawQuery(sql + ContentTable.LIMIT_BY_PAGE,
                            new String[]{StatusContent.DOWNLOADED.getCode() + "",
                                    StatusContent.ERROR.getCode() + "",
                                    StatusContent.MIGRATED.getCode() + "",
                                    start + "", booksPerPage + ""});
                }
                result = populateResult(cursorContent, db);
            } finally {
                closeCursor(cursorContent, db);
            }
        }

        return result;
    }

    public int countAllContent() {
        return countContentByQuery("", Collections.emptyList(), false);
    }

    public int countContentByQuery(String title, List<Attribute> tags, boolean filterFavourites) {
        int count = 0;
        SQLiteDatabase db = null;
        Cursor cursorCount = null;

        synchronized (locker) {
            Timber.d("countContentByQuery");

            try {
                db = getReadableDatabase();
                String sql = buildContentSearchQuery(title, tags, filterFavourites);
                sql = sql.replace("C.*", "COUNT(*)");

                Timber.d("Query : %s", sql);

                cursorCount = db.rawQuery(sql, new String[]{StatusContent.DOWNLOADED.getCode() + "",
                        StatusContent.ERROR.getCode() + "",
                        StatusContent.MIGRATED.getCode() + ""});

                if (cursorCount.moveToFirst()) {
                    count = cursorCount.getInt(0);
                }
            } finally {
                closeCursor(cursorCount, db);
            }
        }

        return count;
    }

    private String buildContentSearchQuery(String title, List<Attribute> metadata, boolean filterFavourites) {
        List<Attribute> params;
        // Reorganize metadata to facilitate processing
        AttributeMap metadataMap = new AttributeMap();
        metadataMap.add(metadata);

        boolean hasTitleFilter = (title != null && title.length() > 0);
        boolean hasSiteFilter = metadataMap.containsKey(AttributeType.SOURCE);
        boolean hasTagFilter = metadataMap.keySet().size() > (hasSiteFilter?1:0);
        boolean isConstructingTagFilter = false;

        // Base criteria in Content table
        StringBuilder sql = new StringBuilder();
        sql.append(ContentTable.SELECT_DOWNLOADS_BASE);

        if (hasSiteFilter) {
            params = metadataMap.get(AttributeType.SOURCE);
            if (params.size() > 0) sql.append(ContentTable.SELECT_DOWNLOADS_SITES.replace("%1",Helper.buildListAsString(params,"'")));
        }

        if (filterFavourites) sql.append(ContentTable.SELECT_DOWNLOADS_FAVS);

        // Title filter -> continue querying Content table
        if (hasTitleFilter) {
            title = '%' + title.replace("'", "''") + '%';
            sql.append(ContentTable.SELECT_DOWNLOADS_TITLE.replace("%2", title));
        }

        // Tags filter -> query attribute table through a join
        if (hasTagFilter) {
            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);

                    if (attrs.size() > 0) {
                        if (isConstructingTagFilter) sql.append(" AND ");
                        sql.append(ContentTable.SELECT_DOWNLOADS_JOINS);
                        sql.append(
                                ContentTable.SELECT_DOWNLOADS_TAGS
                                        .replace("%4", Helper.buildListAsString(attrs))
                                        .replace("%5", attrType.getCode() + "")
                                        .replace("%6", attrs.size() + "")
                        );
                        sql.append("))");
                        isConstructingTagFilter = true;
                    }
                }
            }
        }

        return sql.toString();
    }

    public List<Content> selectContentByExternalRef(Site site, List<String> uniqueIds)
    {
        List<Content> result = new ArrayList<>();

        synchronized (locker) {
            Timber.d("selectContentByExternalRef");
            SQLiteDatabase db = null;

            Cursor cursorContent = null;

            String sql = ContentTable.SELECT_BY_EXTERNAL_REF;

            sql = sql.replace("%1", Helper.buildListAsString(uniqueIds,"'"));

            Timber.v(sql);

            try {
                db = getReadableDatabase();
                cursorContent = db.rawQuery(sql, new String[]{
                        site.getCode() + "",
                        StatusContent.DOWNLOADED.getCode() + "",
                        StatusContent.ERROR.getCode() + "",
                        StatusContent.MIGRATED.getCode() + "",
                        StatusContent.DOWNLOADING.getCode() + "",
                        StatusContent.PAUSED.getCode() + ""
                });

                // looping through all rows and adding to list
                if (cursorContent.moveToFirst()) {

                    do {
                        result.add(populateContent(cursorContent, db, false));
                    } while (cursorContent.moveToNext());
                }
            } finally {
                if (cursorContent != null) {
                    cursorContent.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
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

    private Content populateContent(Cursor cursorContent, SQLiteDatabase db) { return populateContent(cursorContent, db, true); }
    private Content populateContent(Cursor cursorContent, SQLiteDatabase db, boolean getImages) {
        Content content = new Content()
                .setUrl(cursorContent.getString(ContentTable.IDX_URL - 1))
                .setTitle(cursorContent.getString(ContentTable.IDX_TITLE - 1))
                .setQtyPages(cursorContent.getInt(ContentTable.IDX_QTYPAGES - 1))
                .setUploadDate(cursorContent.getLong(ContentTable.IDX_ULDATE - 1))
                .setDownloadDate(cursorContent.getLong(ContentTable.IDX_DLDATE - 1))
                .setStatus(StatusContent.searchByCode(cursorContent.getInt(ContentTable.IDX_STATUSCODE - 1)))
                .setCoverImageUrl(cursorContent.getString(ContentTable.IDX_COVERURL - 1))
                .setSite(Site.searchByCode(cursorContent.getInt(ContentTable.IDX_SITECODE - 1)))
                .setAuthor(cursorContent.getString(ContentTable.IDX_AUTHOR - 1))
                .setStorageFolder(cursorContent.getString(ContentTable.IDX_STORAGE_FOLDER - 1))
                .setFavourite(1 == cursorContent.getInt(ContentTable.IDX_FAVOURITE - 1))
                .setQueryOrder(cursorContent.getPosition());

        if (getImages) content.setImageFiles(selectImageFilesByContentId(db, content.getId()))
                .setAttributes(selectAttributesByContentId(db, content.getId()));

        content.populateAuthor();

        return content;
    }

    private List<ImageFile> selectImageFilesByContentId(SQLiteDatabase db, int id) {
        List<ImageFile> result = Collections.emptyList();
        Cursor cursorImageFiles = null;
        try {
            cursorImageFiles = db.rawQuery(ImageFileTable.SELECT_BY_CONTENT_ID,
                    new String[]{id + ""});

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
        } finally {
            if (cursorImageFiles != null) {
                cursorImageFiles.close();
            }
        }

        return result;
    }

    public SparseIntArray countProcessedImagesById(int contentId) {
        SparseIntArray result = new SparseIntArray();

        synchronized (locker) {
            Timber.d("countProcessedImagesById");

            SQLiteDatabase db = null;
            Cursor cursorContent = null;

            try {
                db = getReadableDatabase();
                cursorContent = db.rawQuery(ImageFileTable.SELECT_PROCESSED_BY_CONTENT_ID, new String[]{contentId + ""});

                if (cursorContent.moveToFirst()) {
                    do {
                        result.append(cursorContent.getInt(0),cursorContent.getInt(1));
                    } while (cursorContent.moveToNext());
                }
            } finally {
                if (cursorContent != null) {
                    cursorContent.close();
                }
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
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
                    result.add(new Attribute()
                            .setUrl(cursorAttributes.getString(1))
                            .setName(cursorAttributes.getString(2))
                            .setType(AttributeType.searchByCode(cursorAttributes.getInt(3))));
                } while (cursorAttributes.moveToNext());
            }
        } finally {
            if (cursorAttributes != null) {
                cursorAttributes.close();
            }
        }

        return result;
    }

    public Attribute selectAttributeById(int id) {
        Attribute result = null;

        synchronized (locker) {
            Timber.d("selectAttributeById");

            SQLiteDatabase db = null;
            Cursor cursorAttributes = null;

            try {
                db = getReadableDatabase();
                cursorAttributes = db.rawQuery(AttributeTable.SELECT_BY_ID, new String[]{id + ""});

                if (cursorAttributes.moveToFirst()) {
                    result = new Attribute()
                            .setUrl(cursorAttributes.getString(1))
                            .setName(cursorAttributes.getString(2))
                            .setType(AttributeType.searchByCode(cursorAttributes.getInt(3)));
                }
            } finally {
                if (cursorAttributes != null) cursorAttributes.close();
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (db != null && db.isOpen()) db.close();
            }
        }

        return result;
    }

    public List<Attribute> selectAvailableAttributes(int type, List<Attribute> attrs, List<Integer> sites, boolean filterFavourites) {
        ArrayList<Attribute> result = new ArrayList<>();

        synchronized (locker) {
            Timber.d("selectAvailableAttributes");
            SQLiteDatabase db = null;

            Cursor cursorAttributes = null;

            String sql = AttributeTable.SELECT_ALL_BY_TYPE;

            if (sites != null && sites.size() > 0)
            {
                sql += AttributeTable.SELECT_ALL_BY_USAGE_SITE_FILTER;
                sql = sql.replace("%1", Helper.buildListAsString(sites, "'"));
            }

            if (filterFavourites) sql += AttributeTable.SELECT_ALL_BY_USAGE_FAVS;

            if (attrs != null && attrs.size() > 0) {
                sql += AttributeTable.SELECT_ALL_BY_USAGE_TAG_FILTER;
                sql = sql.replace("%2", Helper.buildListAsString(attrs,""));
                sql = sql.replace("%3", attrs.size() + "");
            }

            sql += AttributeTable.SELECT_ALL_BY_USAGE_END;

            Timber.v(sql);

            try {
                db = getReadableDatabase();
                cursorAttributes = db.rawQuery(sql, new String[]{type + ""});

                // looping through all rows and adding to list
                if (cursorAttributes.moveToFirst()) {

                    do {
                        result.add(new Attribute(AttributeType.searchByCode(type), cursorAttributes.getString(1), cursorAttributes.getString(2)).setCount(cursorAttributes.getInt(3)) );
                    } while (cursorAttributes.moveToNext());
                }
            } finally {
                if (cursorAttributes != null) {
                    cursorAttributes.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }

    public List<Attribute> selectAllAttributesByType(AttributeType type, String filter) {
        ArrayList<Attribute> result = new ArrayList<>();

        synchronized (locker) {
            Timber.d("selectAllAttributesByType");
            SQLiteDatabase db = null;

            Cursor cursorAttributes = null;

            String sql = AttributeTable.SELECT_ALL_BY_TYPE;

            if (filter != null && filter.trim().length() > 0)
            {
                sql += AttributeTable.SELECT_ALL_BY_USAGE_ATTR_FILTER;
                sql = sql.replace("%2", filter);
            }

            sql += AttributeTable.SELECT_ALL_BY_USAGE_END;

            Timber.v(sql);

            try {
                db = getReadableDatabase();
                cursorAttributes = db.rawQuery(sql, new String[]{type.getCode() + ""});

                // looping through all rows and adding to list
                if (cursorAttributes.moveToFirst()) {

                    do {
                        result.add(new Attribute(type, cursorAttributes.getString(1), "").setExternalId(cursorAttributes.getInt(0)).setCount(cursorAttributes.getInt(3)));
                    } while (cursorAttributes.moveToNext());
                }
            } finally {
                if (cursorAttributes != null) {
                    cursorAttributes.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }

    public List<Attribute> selectAvailableSources() {
        ArrayList<Attribute> result = new ArrayList<>();

        synchronized (locker) {
            Timber.d("selectAvailableSources");
            SQLiteDatabase db = null;

            Cursor cursorAttributes = null;

            try {
                db = getReadableDatabase();
                cursorAttributes = db.rawQuery(ContentTable.SELECT_SOURCES, new String[]{
                        StatusContent.DOWNLOADED.getCode() + "",
                        StatusContent.ERROR.getCode() + "",
                        StatusContent.MIGRATED.getCode() + ""});

                // looping through all rows and adding to list
                if (cursorAttributes.moveToFirst()) {

                    do {
                        Site s = Site.searchByCode(cursorAttributes.getInt(0));
                        if (null != s) result.add(new Attribute(AttributeType.SOURCE, s.getDescription(), "").setExternalId(s.getCode()).setCount(cursorAttributes.getInt(1)));
                    } while (cursorAttributes.moveToNext());
                }
            } finally {
                if (cursorAttributes != null) {
                    cursorAttributes.close();
                }
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }

        return result;
    }

    public void updateImageFileStatus(ImageFile row) {
        synchronized (locker) {
            Timber.d("updateImageFileStatus");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;
            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ImageFileTable.UPDATE_IMAGE_FILE_STATUS_FROM_ID);
                db.beginTransaction();
                try {
                    statement.clearBindings();
                    statement.bindLong(1, row.getStatus().getCode());
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

    public void updateImageFileStatus(Content content, StatusContent updateFrom, StatusContent updateTo) {
        synchronized (locker) {
            Timber.d("updateImageFileStatus2");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ImageFileTable.UPDATE_IMAGE_FILE_STATUS_FROM_ID_AND_STATUS);
                db.beginTransaction();
                try {
                    statement.clearBindings();
                    statement.bindLong(1, updateTo.getCode());
                    statement.bindLong(2, content.getId());
                    statement.bindLong(3, updateFrom.getCode());
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

    private void deleteContent(SQLiteDatabase db, Content content) {
        deleteContent(db, content, true);
    }

    private void deleteContent(SQLiteDatabase db, Content content, boolean deletefromQueue) {
        SQLiteStatement statement = null;
        SQLiteStatement statementImages = null;
        SQLiteStatement statementAttributes = null;
        SQLiteStatement statementQueue = null;

        try {
            statement = db.compileStatement(ContentTable.DELETE_STATEMENT);
            statementImages = db.compileStatement(ImageFileTable.DELETE_STATEMENT);
            statementAttributes = db.compileStatement(ContentAttributeTable.DELETE_STATEMENT);
            if (deletefromQueue) statementQueue = db.compileStatement(QueueTable.DELETE_STATEMENT);

            statement.clearBindings();
            statement.bindLong(1, content.getId());
            statement.execute();
            statementImages.clearBindings();
            statementImages.bindLong(1, content.getId());
            statementImages.execute();
            statementAttributes.clearBindings();
            statementAttributes.bindLong(1, content.getId());
            statementAttributes.execute();
            if (deletefromQueue) {
                statementQueue.clearBindings();
                statementQueue.bindLong(1, content.getId());
                statementQueue.execute();
            }
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (statementImages != null) {
                statementImages.close();
            }
            if (statementAttributes != null) {
                statementAttributes.close();
            }
            if (statementQueue != null) {
                statementQueue.close();
            }
        }
    }

    public void deleteContent(Content content) {
        synchronized (locker) {
            Timber.d("deleteContent");
            SQLiteDatabase db = null;

            try {
                db = getWritableDatabase();
                db.beginTransaction();
                try {
                    deleteContent(db, content);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                Timber.d("Closing db connection. Condition: %s", (db != null && db.isOpen()));
                if (db != null && db.isOpen()) {
                    db.close(); // Closing database connection
                }
            }
        }
    }

    public void updateContentStatus(Content row) {
        synchronized (locker) {
            Timber.d("updateContentStatus");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable
                        .UPDATE_CONTENT_DOWNLOAD_DATE_STATUS_STATEMENT);
                db.beginTransaction();
                try {
                    statement.clearBindings();
                    statement.bindLong(1, row.getDownloadDate());
                    statement.bindLong(2, row.getStatus().getCode());
                    statement.bindLong(3, row.getId());
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

    public void updateContentStorageFolder(Content row) {
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

    public void updateContentFavourite(Content content) {
        synchronized (locker) {
            Timber.d("updateContentFavourite");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(ContentTable.UPDATE_CONTENT_FAVOURITE);

                db.beginTransaction();
                try {
                    statement.clearBindings();
                    statement.bindString(1, content.isFavourite() ? "1" : "0");
                    statement.bindLong(2, content.getId());
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

    public void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
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

    public List<Pair<Integer, Integer>> selectQueue() {
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

    public List<Content> selectQueueContents() {
        ArrayList<Content> result = new ArrayList<>();

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
                        Integer i = cursorQueue.getInt(0);
                        Content content = selectContentById(db, i);
                        if (content != null) result.add(content);
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

    public List<Integer> selectContentsForQueueMigration() {
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

    public void insertQueue(int id, int order) {
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

    public void deleteQueueById(int contentId) {
        synchronized (locker) {
            Timber.d("deleteQueueById");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;

            try {
                db = getWritableDatabase();
                statement = db.compileStatement(QueueTable.DELETE_STATEMENT);

                statement.clearBindings();
                statement.bindLong(1, contentId);
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

    public void udpateQueue(int contentId, int newOrder) {
        synchronized (locker) {
            Timber.d("udpateQueue");
            SQLiteDatabase db = null;
            SQLiteStatement statement = null;


            try {
                db = getWritableDatabase();
                statement = db.compileStatement(QueueTable.UPDATE_STATEMENT);

                statement.clearBindings();
                statement.bindLong(1, newOrder);
                statement.bindLong(2, contentId);
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
}