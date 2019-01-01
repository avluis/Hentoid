package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Content Table
 */
public abstract class ContentTable {

    public static final String TABLE_NAME = "content";

    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
    public static final String LIMIT_BY_PAGE = " LIMIT ?,?";

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
    public static final String SOURCE_COLUMN = "site";
    public static final String AUTHOR_COLUMN = "author";
    public static final String STORAGE_FOLDER_COLUMN = "storage_folder";
    public static final String FAVOURITE_COLUMN = "favourite";
    public static final String READS_COLUMN = "reads";
    public static final String LAST_READ_DATE_COLUMN = "last_read_date";

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

    // ORDER
    public static final String ORDER_BY_DATE = " ORDER BY C." + DOWNLOAD_DATE_COLUMN;
    public static final String ORDER_ALPHABETIC = " ORDER BY C." + TITLE_COLUMN;
    public static final String ORDER_READS_ASC = " ORDER BY C." + READS_COLUMN + ", C." + LAST_READ_DATE_COLUMN;
    public static final String ORDER_READS_DESC = " ORDER BY C." + READS_COLUMN + " DESC, C." + LAST_READ_DATE_COLUMN + " DESC";
    public static final String ORDER_READ_DATE = " ORDER BY C." + LAST_READ_DATE_COLUMN + " DESC";
    public static final String ORDER_RANDOM = " ORDER BY ((ABS(" + ID_COLUMN + " * %6) * 1e7) % 1e7)";

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + ID_COLUMN + " INTEGER PRIMARY KEY," + UNIQUE_SITE_ID_COLUMN + " TEXT,"
            + CATEGORY_COLUMN + " TEXT," + URL_COLUMN + " TEXT," + HTML_DESCRIPTION_COLUMN
            + " TEXT," + TITLE_COLUMN + " TEXT" + "," + QTY_PAGES_COLUMN + " INTEGER" + ","
            + UPLOAD_DATE_COLUMN + " INTEGER" + "," + DOWNLOAD_DATE_COLUMN + " INTEGER" + ","
            + STATUS_COLUMN + " INTEGER" + "," + COVER_IMAGE_URL_COLUMN + " TEXT"
            + "," + SOURCE_COLUMN + " INTEGER, " + AUTHOR_COLUMN + " TEXT, " + STORAGE_FOLDER_COLUMN + " TEXT, "
            + FAVOURITE_COLUMN + " INTEGER, " + READS_COLUMN + " INTEGER, " + LAST_READ_DATE_COLUMN + " INTEGER "
            + " DEFAULT 0 )";

    // DELETE
    public static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE " + ID_COLUMN + " = ?";


    // UPDATE
    public static final String UPDATE_CONTENT_DOWNLOAD_DATE_STATUS_STATEMENT = "UPDATE "
            + TABLE_NAME + " SET " + DOWNLOAD_DATE_COLUMN + " = ?, " + STATUS_COLUMN
            + " = ? WHERE " + ID_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_STATUS_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
            + STATUS_COLUMN + " = ? WHERE " + STATUS_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_STORAGE_FOLDER = "UPDATE " + TABLE_NAME + " SET " + STORAGE_FOLDER_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_FAVOURITE = "UPDATE " + TABLE_NAME + " SET " + FAVOURITE_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_READS = "UPDATE " + TABLE_NAME + " SET " + READS_COLUMN + " = ?, " + LAST_READ_DATE_COLUMN + "= ? WHERE " + ID_COLUMN + " = ?";


    // SELECT
    public static final String SELECT_BY_CONTENT_ID = "SELECT * FROM " + TABLE_NAME + " C WHERE C." + ID_COLUMN + " = ?";

    public static final String SELECT_BY_EXTERNAL_REF = "SELECT * FROM " + TABLE_NAME + " WHERE " + SOURCE_COLUMN + "= ? AND " + UNIQUE_SITE_ID_COLUMN + " IN (%1) AND " + STATUS_COLUMN + " IN (?,?,?,?,?)";

    public static final String SELECT_NULL_FOLDERS = "SELECT * FROM " + TABLE_NAME + " WHERE " + STORAGE_FOLDER_COLUMN + " is null";

    public static final String SELECT_SOURCES = "SELECT " + SOURCE_COLUMN + ", COUNT(*) FROM " + TABLE_NAME + " WHERE " + STATUS_COLUMN + " IN (?,?,?) GROUP BY 1";

    public static final String SELECT_BY_STATUS = "SELECT * FROM " + TABLE_NAME + " C WHERE C." + STATUS_COLUMN + " = ?";


    // SEARCH QUERIES "TOOLBOX"

    public static final String SELECT_DOWNLOADS_BASE = "SELECT C.* FROM " + TABLE_NAME + " C WHERE C." + STATUS_COLUMN + " in (?, ?, ?) ";

    public static final String SELECT_DOWNLOADS_SITES = " AND C." + SOURCE_COLUMN + " in (%1) ";

    public static final String SELECT_DOWNLOADS_FAVS = " AND C." + FAVOURITE_COLUMN + " = 1 ";


    private static final String SELECT_DOWNLOADS_TITLE_RAW = " lower(C." + TITLE_COLUMN + ") LIKE '%2' ";
    public static final String SELECT_DOWNLOADS_TITLE = " AND " + SELECT_DOWNLOADS_TITLE_RAW;
    public static final String SELECT_DOWNLOADS_TITLE_UNIVERSAL = " AND (" + SELECT_DOWNLOADS_TITLE_RAW;

    public static final String SELECT_DOWNLOADS_JOINS = " AND C." + ID_COLUMN
            + " in (SELECT " + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM (" + "SELECT CA." + ContentAttributeTable.CONTENT_ID_COLUMN + " , COUNT(*) FROM " // TODO replace that IN by an INNER JOIN
            + ContentAttributeTable.TABLE_NAME + " CA INNER JOIN " + AttributeTable.TABLE_NAME
            + " A ON CA." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " = A." + AttributeTable.ID_COLUMN + " WHERE ";

    public static final String SELECT_DOWNLOADS_JOINS_UNIVERSAL = " OR C." + ID_COLUMN
            + " in (SELECT " + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM (" + "SELECT CA." + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM " // TODO replace that IN by an INNER JOIN
            + ContentAttributeTable.TABLE_NAME + " CA INNER JOIN " + AttributeTable.TABLE_NAME
            + " A ON CA." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " = A." + AttributeTable.ID_COLUMN + " WHERE ";

    public static final String SELECT_DOWNLOADS_TAGS = "(lower(A." + AttributeTable.NAME_COLUMN + ") in (%4) AND A."
            + AttributeTable.TYPE_COLUMN + " = %5) GROUP BY 1 HAVING COUNT(*)=%6 ))";
    public static final String SELECT_DOWNLOADS_TAGS_UNIVERSAL = "lower(A." + AttributeTable.NAME_COLUMN + ") LIKE lower('%4') ) ))";
}
