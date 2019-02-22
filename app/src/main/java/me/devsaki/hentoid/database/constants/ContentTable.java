package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Content Table
 */
public abstract class ContentTable {

    public static final String TABLE_NAME = "content";

    // COLUMN NAMES
    static final String ID_COLUMN = "id";
    private static final String UNIQUE_SITE_ID_COLUMN = "unique_site_id";
    private static final String CATEGORY_COLUMN = "category";
    private static final String URL_COLUMN = "url";
    private static final String HTML_DESCRIPTION_COLUMN = "html_description";
    private static final String TITLE_COLUMN = "title";
    private static final String QTY_PAGES_COLUMN = "qty_pages";
    private static final String UPLOAD_DATE_COLUMN = "upload_date";
    public static final String DOWNLOAD_DATE_COLUMN = "download_date";
    static final String STATUS_COLUMN = "status";
    private static final String COVER_IMAGE_URL_COLUMN = "cover_image_url";
    private static final String SOURCE_COLUMN = "site";
    public static final String AUTHOR_COLUMN = "author";
    public static final String STORAGE_FOLDER_COLUMN = "storage_folder";
    public static final String FAVOURITE_COLUMN = "favourite";
    public static final String READS_COLUMN = "reads";
    public static final String LAST_READ_DATE_COLUMN = "last_read_date";
    public static final String DOWNLOAD_PARAMS_COLUMN = "download_params";

    // COLUMN INDEXES
    public static final int IDX_INTERNALID = 1;
    public static final int IDX_SITEID = 2;
    public static final int IDX_CATEGORY = 3;
    public static final int IDX_URL = 4;
    public static final int IDX_HTML_DESCRIPTION = 5;
    public static final int IDX_TITLE = 6;
    public static final int IDX_QTYPAGES = 7;
    public static final int IDX_ULDATE = 8;
    public static final int IDX_DLDATE = 9;
    public static final int IDX_STATUSCODE = 10;
    public static final int IDX_COVERURL = 11;
    public static final int IDX_SOURCECODE = 12;
    public static final int IDX_AUTHOR = 13;
    public static final int IDX_STORAGE_FOLDER = 14;
    public static final int IDX_FAVOURITE = 15;
    public static final int IDX_READS = 16;
    public static final int IDX_LAST_READ_DATE = 17;
    public static final int IDX_DOWNLOAD_PARAMS = 18;

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + ID_COLUMN + " INTEGER PRIMARY KEY," + UNIQUE_SITE_ID_COLUMN + " TEXT,"
            + CATEGORY_COLUMN + " TEXT," + URL_COLUMN + " TEXT," + HTML_DESCRIPTION_COLUMN
            + " TEXT," + TITLE_COLUMN + " TEXT" + "," + QTY_PAGES_COLUMN + " INTEGER" + ","
            + UPLOAD_DATE_COLUMN + " INTEGER" + "," + DOWNLOAD_DATE_COLUMN + " INTEGER" + ","
            + STATUS_COLUMN + " INTEGER" + "," + COVER_IMAGE_URL_COLUMN + " TEXT"
            + "," + SOURCE_COLUMN + " INTEGER, " + AUTHOR_COLUMN + " TEXT, " + STORAGE_FOLDER_COLUMN + " TEXT, "
            + FAVOURITE_COLUMN + " INTEGER, " + READS_COLUMN + " INTEGER, " + LAST_READ_DATE_COLUMN + " INTEGER, "
            + DOWNLOAD_PARAMS_COLUMN + " TEXT "
            + " DEFAULT 0 )";


    // UPDATE
    public static final String UPDATE_CONTENT_STATUS_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
            + STATUS_COLUMN + " = ? WHERE " + STATUS_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_STORAGE_FOLDER = "UPDATE " + TABLE_NAME + " SET " + STORAGE_FOLDER_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?";


    // SELECT
    public static final String SELECT_BY_CONTENT_ID = "SELECT * FROM " + TABLE_NAME + " C WHERE C." + ID_COLUMN + " = ?";

    public static final String SELECT_NULL_FOLDERS = "SELECT * FROM " + TABLE_NAME + " WHERE " + STORAGE_FOLDER_COLUMN + " is null";

    public static final String SELECT_MIGRABLE_CONTENT = "SELECT " + ID_COLUMN + " FROM " + TABLE_NAME + " WHERE " + STATUS_COLUMN + " IN (?,?,?,?,?)";
}
