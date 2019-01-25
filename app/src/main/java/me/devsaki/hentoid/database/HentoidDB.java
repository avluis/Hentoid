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

    private static final int DATABASE_VERSION = 7;
    private static HentoidDB instance;
    private SQLiteDatabase mDatabase;
    private int mOpenCounter;


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

    public long countContentEntries() {
        long count;

        try {
            SQLiteDatabase db = openDatabase();
            count = DatabaseUtils.queryNumEntries(db, ContentTable.TABLE_NAME);
        } finally {
            closeDatabase();
        }

        return count;
    }

    SparseIntArray countAttributesPerType() {
        return countAttributesPerType(null);
    }

    SparseIntArray countAttributesPerType(List<Attribute> filter) {
        SparseIntArray result = new SparseIntArray();

        Timber.d("countAttributesPerType");

        StringBuilder sql = new StringBuilder(AttributeTable.SELECT_COUNT_BY_TYPE_SELECT);

        if (filter != null && !filter.isEmpty()) {
            AttributeMap metadataMap = new AttributeMap();
            metadataMap.add(filter);

            List<Attribute> params = metadataMap.get(AttributeType.SOURCE);
            if (params != null && !params.isEmpty())
                sql.append(AttributeTable.SELECT_COUNT_BY_TYPE_SOURCE_FILTER.replace("@1", Helper.buildListAsString(Helper.extractAttributesIds(params), "'")));

            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);
                    if (attrs.size() > 0)
                        sql.append(AttributeTable.SELECT_COUNT_BY_TYPE_ATTR_FILTER_JOINS);
                    sql.append(
                            AttributeTable.SELECT_COUNT_BY_TYPE_ATTR_FILTER_ATTRS
                                    .replace("@4", Helper.buildListAsString(attrs, "'"))
                                    .replace("@5", attrType.getCode() + "")
                                    .replace("@6", attrs.size() + "")
                    );
                }
            }
        }

        sql.append(AttributeTable.SELECT_COUNT_BY_TYPE_GROUP);

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(sql.toString(), new String[]{})) {

            if (cursorContent.moveToFirst()) {
                do {
                    result.append(cursorContent.getInt(0), cursorContent.getInt(1));
                } while (cursorContent.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    public void insertContent(Content row) {
        insertContents(new Content[]{row});
    }

    public void insertContents(Content[] rows) {
        Timber.d("insertContents");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ContentTable.INSERT_STATEMENT)) {
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

                    statement.bindLong(ContentTable.IDX_SOURCECODE, row.getSite().getCode());
                    statement.bindString(ContentTable.IDX_AUTHOR, (null == row.getAuthor()) ? "" : row.getAuthor());
                    statement.bindString(ContentTable.IDX_STORAGE_FOLDER, (null == row.getStorageFolder()) ? "" : row.getStorageFolder());
                    statement.bindLong(ContentTable.IDX_FAVOURITE, row.isFavourite() ? 1 : 0);
                    statement.bindLong(ContentTable.IDX_READS, row.getReads());
                    statement.bindLong(ContentTable.IDX_LAST_READ_DATE, row.getLastReadDate());

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
            closeDatabase();
        }
    }

    public void insertImageFiles(Content content) {
        Timber.d("insertImageFiles");
        SQLiteStatement statement = null;
        SQLiteStatement statementImages = null;

        try {
            SQLiteDatabase db = openDatabase();
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
            if (statement != null) {
                statement.close();
            }
            if (statementImages != null) {
                statementImages.close();
            }

            closeDatabase();
        }
    }

    private void insertAttributes(SQLiteDatabase db, Content content, List<Attribute> rows) {

        try (SQLiteStatement statement = db.compileStatement(AttributeTable.INSERT_STATEMENT);
             SQLiteStatement statementContentAttribute = db.compileStatement(ContentAttributeTable.INSERT_STATEMENT)) {

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
        }
    }

    private void insertImageFiles(SQLiteDatabase db, Content content) {
        try (SQLiteStatement statement = db.compileStatement(ImageFileTable.INSERT_STATEMENT)) {
            insertImageFiles(statement, content);
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
    private Content selectContentById(SQLiteDatabase db, int id) {
        Content result = null;

        try (Cursor cursorContents = db.rawQuery(ContentTable.SELECT_BY_CONTENT_ID, new String[]{id + ""})) {

            if (cursorContents.moveToFirst()) {
                result = populateContent(cursorContents, db);
            }
        }

        return result;
    }

    public List<Content> selectContentEmptyFolder() {
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

    // This is a long running task, execute with AsyncTask or similar
    List<Content> selectContentByQuery(String title, int page, int booksPerPage, List<Attribute> tags, boolean filterFavourites, int orderStyle) {
        List<Content> result = Collections.emptyList();

        Timber.d("selectContentByQuery");

        int start = (page - 1) * booksPerPage;
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
            case Preferences.Constant.PREF_ORDER_CONTENT_LEAST_READ:
                sql += ContentTable.ORDER_READS_ASC;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_MOST_READ:
                sql += ContentTable.ORDER_READS_DESC;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_READ:
                sql += ContentTable.ORDER_READ_DATE;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_RANDOM:
                sql += ContentTable.ORDER_RANDOM.replace("@6", String.valueOf(RandomSeedSingleton.getInstance().getRandomNumber()));
                break;
            default:
                // Nothing
        }

        Timber.d("Query : %s; %s, %s", sql, start, booksPerPage);
        String[] arguments;


        if (booksPerPage < 0) arguments = new String[]{StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + ""};
        else
            arguments =
                    new String[]{StatusContent.DOWNLOADED.getCode() + "",
                            StatusContent.ERROR.getCode() + "",
                            StatusContent.MIGRATED.getCode() + "",
                            start + "", booksPerPage + ""};

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(sql + ContentTable.LIMIT_BY_PAGE, arguments)) {

            result = populateResult(cursorContent, db);
        } finally {
            closeDatabase();
        }

        return result;
    }

    // This is a long running task, execute with AsyncTask or similar
    List<Content> selectContentByUniqueQuery(String query, int page, int booksPerPage, boolean filterFavourites, int orderStyle) {
        List<Content> result = Collections.emptyList();

        Timber.d("selectContentByUniqueQuery");

        int start = (page - 1) * booksPerPage;
        String sql = buildUniversalContentSearchQuery(query, filterFavourites);

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
            case Preferences.Constant.PREF_ORDER_CONTENT_LEAST_READ:
                sql += ContentTable.ORDER_READS_ASC;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_MOST_READ:
                sql += ContentTable.ORDER_READS_DESC;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_READ:
                sql += ContentTable.ORDER_READ_DATE;
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_RANDOM:
                sql += ContentTable.ORDER_RANDOM.replace("@6", String.valueOf(RandomSeedSingleton.getInstance().getRandomNumber()));
                break;
            default:
                // Nothing
        }

        Timber.d("Query : %s; %s, %s", sql, start, booksPerPage);

        String[] arguments;
        if (booksPerPage < 0) arguments = new String[]{StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + ""};
        else arguments = new String[]{StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + "",
                start + "", booksPerPage + ""};

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(sql, arguments)) {
            result = populateResult(cursorContent, db);
        } finally {
            closeDatabase();
        }

        return result;
    }

    int countAllContent() {
        return countContentByQuery("", Collections.emptyList(), false);
    }

    int countContentByQuery(String title, List<Attribute> tags, boolean filterFavourites) {
        int count = 0;

        Timber.d("countContentByQuery");

        String sql = buildContentSearchQuery(title, tags, filterFavourites);
        sql = sql.replace("C.*", "COUNT(*)");

        Timber.d("Query : %s", sql);

        SQLiteDatabase db = openDatabase();

        try (Cursor cursorCount = db.rawQuery(sql, new String[]{StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + ""})) {

            if (cursorCount.moveToFirst()) {
                count = cursorCount.getInt(0);
            }
        } finally {
            closeDatabase();
        }

        return count;
    }

    int countContentByUniqueQuery(String query, boolean filterFavourites) {
        int count = 0;

        Timber.d("countContentByQuery");

        String sql = buildUniversalContentSearchQuery(query, filterFavourites);
        sql = sql.replace("C.*", "COUNT(*)");

        Timber.d("Query : %s", sql);

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorCount = db.rawQuery(sql, new String[]{StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + ""})) {

            if (cursorCount.moveToFirst()) {
                count = cursorCount.getInt(0);
            }
        } finally {
            closeDatabase();
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
        boolean hasTagFilter = metadataMap.keySet().size() > (hasSiteFilter ? 1 : 0);

        // Base criteria in Content table
        StringBuilder sql = new StringBuilder();
        sql.append(ContentTable.SELECT_DOWNLOADS_BASE);

        if (hasSiteFilter) {
            params = metadataMap.get(AttributeType.SOURCE);
            if (params.size() > 0)
                sql.append(ContentTable.SELECT_DOWNLOADS_SITES.replace("@1", Helper.buildListAsString(Helper.extractAttributesIds(params), "'")));
        }

        if (filterFavourites) sql.append(ContentTable.SELECT_DOWNLOADS_FAVS);

        // Title filter -> continue querying Content table
        if (hasTitleFilter) {
            title = '%' + title.replace("'", "''") + '%';
            sql.append(ContentTable.SELECT_DOWNLOADS_TITLE.replace("@2", title));
        }

        // Tags filter -> query attribute table through a join
        if (hasTagFilter) {
            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);

                    if (attrs.size() > 0) {
                        sql.append(ContentTable.SELECT_DOWNLOADS_JOINS);
                        sql.append(
                                ContentTable.SELECT_DOWNLOADS_TAGS
                                        .replace("@4", Helper.buildListAsString(attrs, "'"))
                                        .replace("@5", attrType.getCode() + "")
                                        .replace("@6", attrs.size() + "")
                        );
                    }
                }
            }
        }

        return sql.toString();
    }

    private String buildUniversalContentSearchQuery(String query, boolean filterFavourites) {
        // Base criteria in Content table
        StringBuilder sql = new StringBuilder();
        sql.append(ContentTable.SELECT_DOWNLOADS_BASE);

        if (filterFavourites) sql.append(ContentTable.SELECT_DOWNLOADS_FAVS);

        query = '%' + query.replace("'", "''") + '%';

        sql.append(ContentTable.SELECT_DOWNLOADS_TITLE_UNIVERSAL.replace("@2", query));
        sql.append(ContentTable.SELECT_DOWNLOADS_JOINS_UNIVERSAL);
        sql.append(ContentTable.SELECT_DOWNLOADS_TAGS_UNIVERSAL.replace("@4", query));

        return sql.toString();
    }

    public List<Content> selectContentByExternalRef(Site site, List<String> uniqueIds) {
        List<Content> result = new ArrayList<>();

        Timber.d("selectContentByExternalRef");
        String sql = ContentTable.SELECT_BY_EXTERNAL_REF;

        sql = sql.replace("@1", Helper.buildListAsString(uniqueIds, "'"));

        Timber.v(sql);

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(sql, new String[]{
                site.getCode() + "",
                StatusContent.DOWNLOADED.getCode() + "",
                StatusContent.ERROR.getCode() + "",
                StatusContent.MIGRATED.getCode() + "",
                StatusContent.DOWNLOADING.getCode() + "",
                StatusContent.PAUSED.getCode() + ""
        })) {

            // looping through all rows and adding to list
            if (cursorContent.moveToFirst()) {

                do {
                    result.add(populateContent(cursorContent, db, false));
                } while (cursorContent.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    public List<Content> selectContentByStatus(StatusContent status) {
        List<Content> result = new ArrayList<>();

        Timber.d("selectContentByStatus");
        String sql = ContentTable.SELECT_BY_STATUS;

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(sql, new String[]{
                status.getCode() + ""
        })) {

            // looping through all rows and adding to list
            if (cursorContent.moveToFirst()) {

                do {
                    result.add(populateContent(cursorContent, db, false));
                } while (cursorContent.moveToNext());
            }
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
        return populateContent(cursorContent, db, true);
    }

    private Content populateContent(Cursor cursorContent, SQLiteDatabase db, boolean getImages) {
        Content content = new Content()
                .setUrl(cursorContent.getString(ContentTable.IDX_URL - 1))
                .setTitle(cursorContent.getString(ContentTable.IDX_TITLE - 1))
                .setQtyPages(cursorContent.getInt(ContentTable.IDX_QTYPAGES - 1))
                .setUploadDate(cursorContent.getLong(ContentTable.IDX_ULDATE - 1))
                .setDownloadDate(cursorContent.getLong(ContentTable.IDX_DLDATE - 1))
                .setStatus(StatusContent.searchByCode(cursorContent.getInt(ContentTable.IDX_STATUSCODE - 1)))
                .setCoverImageUrl(cursorContent.getString(ContentTable.IDX_COVERURL - 1))
                .setSite(Site.searchByCode(cursorContent.getInt(ContentTable.IDX_SOURCECODE - 1)))
                .setAuthor(cursorContent.getString(ContentTable.IDX_AUTHOR - 1))
                .setStorageFolder(cursorContent.getString(ContentTable.IDX_STORAGE_FOLDER - 1))
                .setFavourite(1 == cursorContent.getInt(ContentTable.IDX_FAVOURITE - 1))
                .setReads(cursorContent.getLong(ContentTable.IDX_READS - 1))
                .setLastReadDate(cursorContent.getLong(ContentTable.IDX_LAST_READ_DATE - 1))
                .setQueryOrder(cursorContent.getPosition());

        if (getImages) content.setImageFiles(selectImageFilesByContentId(db, content.getId()))
                .setAttributes(selectAttributesByContentId(db, content.getId()));

        content.populateAuthor();

        return content;
    }

    private List<ImageFile> selectImageFilesByContentId(SQLiteDatabase db, int id) {
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

    public SparseIntArray countProcessedImagesById(int contentId) {
        SparseIntArray result = new SparseIntArray();

        Timber.d("countProcessedImagesById");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorContent = db.rawQuery(ImageFileTable.SELECT_PROCESSED_BY_CONTENT_ID, new String[]{contentId + ""})) {

            if (cursorContent.moveToFirst()) {
                do {
                    result.append(cursorContent.getInt(0), cursorContent.getInt(1));
                } while (cursorContent.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    private AttributeMap selectAttributesByContentId(SQLiteDatabase db, int id) {
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

    public Attribute selectAttributeById(int id) {
        Attribute result = null;

        Timber.d("selectAttributeById");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorAttributes = db.rawQuery(AttributeTable.SELECT_BY_ID, new String[]{id + ""})) {

            if (cursorAttributes.moveToFirst()) {
                result = new Attribute()
                        .setUrl(cursorAttributes.getString(1))
                        .setName(cursorAttributes.getString(2))
                        .setType(AttributeType.searchByCode(cursorAttributes.getInt(3)));
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    List<Attribute> selectAvailableAttributes(AttributeType type, List<Attribute> attributes, String filter, boolean filterFavourites) {
        ArrayList<Attribute> result = new ArrayList<>();

        Timber.d("selectAvailableAttributes");
        String sql = AttributeTable.SELECT_ALL_BY_TYPE;

        if (attributes != null) {
            // Detect the presence of sources within given attributes
            List<Integer> sources = new ArrayList<>();
            List<Attribute> attrs = new ArrayList<>();
            for (Attribute a : attributes)
                if (a.getType().equals(AttributeType.SOURCE)) sources.add(a.getId());
                else attrs.add(a);

            if (filter != null && !filter.trim().isEmpty()) {
                sql += AttributeTable.SELECT_ALL_ATTR_FILTER;
                sql = sql.replace("@2", filter);
            }

            if (sources.size() > 0) {
                sql += AttributeTable.SELECT_ALL_SOURCE_FILTER;
                sql = sql.replace("@1", Helper.buildListAsString(sources, ""));
            }

            if (attrs.size() > 0) {
                sql += AttributeTable.SELECT_ALL_BY_USAGE_TAG_FILTER;
                sql = sql.replace("@2", Helper.buildListAsString(attrs, "'"));
                sql = sql.replace("@3", attrs.size() + "");
            }
        }

        if (filterFavourites) sql += AttributeTable.SELECT_ALL_BY_USAGE_FAVS;

        sql += AttributeTable.SELECT_ALL_BY_USAGE_END;

        Timber.v(sql);

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorAttributes = db.rawQuery(sql, new String[]{type.getCode() + ""})) {

            // looping through all rows and adding to list
            if (cursorAttributes.moveToFirst()) {

                do {
                    result.add(new Attribute(AttributeType.searchByCode(type.getCode()), cursorAttributes.getString(1), cursorAttributes.getString(2)).setCount(cursorAttributes.getInt(3)));
                } while (cursorAttributes.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    List<Attribute> selectAllAttributesByType(AttributeType type, String filter) {
        ArrayList<Attribute> result = new ArrayList<>();

        Timber.d("selectAllAttributesByType");

        String sql = AttributeTable.SELECT_ALL_BY_TYPE;

        if (filter != null && !filter.trim().isEmpty()) {
            sql += AttributeTable.SELECT_ALL_ATTR_FILTER;
            sql = sql.replace("@2", filter);
        }

        sql += AttributeTable.SELECT_ALL_BY_USAGE_END;

        Timber.v(sql);

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorAttributes = db.rawQuery(sql, new String[]{type.getCode() + ""})) {

            // looping through all rows and adding to list
            if (cursorAttributes.moveToFirst()) {

                do {
                    result.add(new Attribute(type, cursorAttributes.getString(1), "").setExternalId(cursorAttributes.getInt(0)).setCount(cursorAttributes.getInt(3)));
                } while (cursorAttributes.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    List<Attribute> selectAvailableSources() {
        return selectAvailableSources(null);
    }

    List<Attribute> selectAvailableSources(List<Attribute> filter) {
        ArrayList<Attribute> result = new ArrayList<>();
        Timber.d("selectAvailableSources");

        StringBuilder sql = new StringBuilder(AttributeTable.SELECT_COUNT_BY_SOURCE_SELECT);

        if (filter != null && !filter.isEmpty()) {
            AttributeMap metadataMap = new AttributeMap();
            metadataMap.add(filter);

            List<Attribute> params = metadataMap.get(AttributeType.SOURCE);
            if (params != null && !params.isEmpty())
                sql.append(AttributeTable.SELECT_COUNT_BY_SOURCE_SOURCE_FILTER.replace("@1", Helper.buildListAsString(Helper.extractAttributesIds(params), "'")));

            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);
                    if (attrs.size() > 0)
                        sql.append(AttributeTable.SELECT_COUNT_BY_SOURCE_ATTR_FILTER_JOINS);
                    sql.append(
                            AttributeTable.SELECT_COUNT_BY_SOURCE_ATTR_FILTER_ATTRS
                                    .replace("@4", Helper.buildListAsString(attrs, "'"))
                                    .replace("@5", attrType.getCode() + "")
                                    .replace("@6", attrs.size() + "")
                    );
                }
            }
        }

        sql.append(AttributeTable.SELECT_COUNT_BY_SOURCE_GROUP);

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorAttributes = db.rawQuery(sql.toString(), new String[]{})) {

            // looping through all rows and adding to list
            if (cursorAttributes.moveToFirst()) {

                do {
                    Site s = Site.searchByCode(cursorAttributes.getInt(0));
                    if (null != s)
                        result.add(new Attribute(AttributeType.SOURCE, s.getDescription(), "").setExternalId(s.getCode()).setCount(cursorAttributes.getInt(1)));
                } while (cursorAttributes.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    public void updateImageFileStatus(ImageFile row) {

        Timber.d("updateImageFileStatus");
        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ImageFileTable.UPDATE_IMAGE_FILE_STATUS_FROM_ID)) {
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
            closeDatabase();
        }
    }

    public void updateImageFileStatus(Content content, StatusContent updateFrom, StatusContent updateTo) {
        Timber.d("updateImageFileStatus2");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ImageFileTable.UPDATE_IMAGE_FILE_STATUS_FROM_ID_AND_STATUS)) {
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
            closeDatabase();
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
        Timber.d("deleteContent");
        SQLiteDatabase db = openDatabase();
        try {
            db.beginTransaction();
            try {
                deleteContent(db, content);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            closeDatabase();
        }
    }

    public void updateContentStatus(Content row) {
        Timber.d("updateContentStatus");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ContentTable
                .UPDATE_CONTENT_DOWNLOAD_DATE_STATUS_STATEMENT)) {
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
            closeDatabase();
        }
    }

    public void updateContentStorageFolder(Content row) {
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

    public void updateContentFavourite(Content content) {
        Timber.d("updateContentFavourite");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ContentTable.UPDATE_CONTENT_FAVOURITE)) {

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
            closeDatabase();
        }
    }

    public void updateContentReads(Content content) {
        Timber.d("updateContentReads");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(ContentTable.UPDATE_CONTENT_READS)) {

            db.beginTransaction();
            try {
                statement.clearBindings();
                statement.bindLong(1, content.getReads());
                statement.bindLong(2, content.getLastReadDate());
                statement.bindLong(3, content.getId());
                statement.execute();
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            closeDatabase();
        }
    }


    public void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
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

    public List<Pair<Integer, Integer>> selectQueue() {
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

    public List<Content> selectQueueContents() {
        ArrayList<Content> result = new ArrayList<>();

        Timber.d("selectQueue");

        SQLiteDatabase db = openDatabase();
        try (Cursor cursorQueue = db.rawQuery(QueueTable.SELECT_QUEUE, new String[]{})) {

            // looping through all rows and adding to list
            if (cursorQueue.moveToFirst()) {
                do {
                    Integer i = cursorQueue.getInt(0);
                    Content content = selectContentById(db, i);
                    if (content != null) result.add(content);
                } while (cursorQueue.moveToNext());
            }
        } finally {
            closeDatabase();
        }

        return result;
    }

    public List<Integer> selectContentsForQueueMigration() {
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

    public void insertQueue(int id, int order) {
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

    public void deleteQueueById(int contentId) {
        Timber.d("deleteQueueById");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(QueueTable.DELETE_STATEMENT)) {

            statement.clearBindings();
            statement.bindLong(1, contentId);
            statement.execute();
        } finally {
            closeDatabase();
        }
    }

    public void udpateQueue(int contentId, int newOrder) {
        Timber.d("udpateQueue");

        SQLiteDatabase db = openDatabase();
        try (SQLiteStatement statement = db.compileStatement(QueueTable.UPDATE_STATEMENT)) {

            statement.clearBindings();
            statement.bindLong(1, newOrder);
            statement.bindLong(2, contentId);
            statement.execute();
        } finally {
            closeDatabase();
        }
    }
}