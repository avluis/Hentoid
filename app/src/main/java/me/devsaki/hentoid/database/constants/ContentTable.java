package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Content Table
 */
public abstract class ContentTable {

    public static final String TABLE_NAME = "content";

    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
    public static final String LIMIT_BY_PAGE = " LIMIT ?,?";

    // COLUMN NAMES
    public static final String ID_COLUMN = "id";
    private static final String UNIQUE_SITE_ID_COLUMN = "unique_site_id";
    private static final String CATEGORY_COLUMN = "category";
    private static final String URL_COLUMN = "url";
    private static final String HTML_DESCRIPTION_COLUMN = "html_description";
    private static final String TITLE_COLUMN = "title";
    private static final String QTY_PAGES_COLUMN = "qty_pages";
    private static final String UPLOAD_DATE_COLUMN = "upload_date";
    private static final String DOWNLOAD_DATE_COLUMN = "download_date";
    public static final String STATUS_COLUMN = "status";
    private static final String COVER_IMAGE_URL_COLUMN = "cover_image_url";
    public static final String SITE_COLUMN = "site";
    private static final String AUTHOR_COLUMN = "author";
    private static final String STORAGE_FOLDER_COLUMN = "storage_folder";
    
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
    public static final int IDX_SITECODE = 12;
    public static final int IDX_AUTHOR = 13;
    public static final int IDX_STORAGE_FOLDER = 14;

    // ORDER
    public static final String ORDER_BY_DATE = " ORDER BY C." + DOWNLOAD_DATE_COLUMN;
    public static final String ORDER_ALPHABETIC = " ORDER BY C." + TITLE_COLUMN;
    public static final String ORDER_RANDOM = " ORDER BY RANDOM()";

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + ID_COLUMN + " INTEGER PRIMARY KEY," + UNIQUE_SITE_ID_COLUMN + " TEXT,"
            + CATEGORY_COLUMN + " TEXT," + URL_COLUMN + " TEXT," + HTML_DESCRIPTION_COLUMN
            + " TEXT," + TITLE_COLUMN + " TEXT" + "," + QTY_PAGES_COLUMN + " INTEGER" + ","
            + UPLOAD_DATE_COLUMN + " INTEGER" + "," + DOWNLOAD_DATE_COLUMN + " INTEGER" + ","
            + STATUS_COLUMN + " INTEGER" + "," + COVER_IMAGE_URL_COLUMN + " TEXT"
            + "," + SITE_COLUMN + " INTEGER, " + AUTHOR_COLUMN + " TEXT, " + STORAGE_FOLDER_COLUMN + " TEXT )";

    // DELETE
    public static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE " + ID_COLUMN + " = ?";


    // UPDATE8
    public static final String UPDATE_CONTENT_DOWNLOAD_DATE_STATUS_STATEMENT = "UPDATE "
            + TABLE_NAME + " SET " + DOWNLOAD_DATE_COLUMN + " = ?, " + STATUS_COLUMN
            + " = ? WHERE " + ID_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_STATUS_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
            + STATUS_COLUMN + " = ? WHERE " + STATUS_COLUMN + " = ?";

    public static final String UPDATE_CONTENT_STORAGE_FOLDER = "UPDATE " + TABLE_NAME + " SET " + STORAGE_FOLDER_COLUMN + " = ? WHERE " + ID_COLUMN +" = ?";


    // SELECT
    public static final String SELECT_BY_CONTENT_ID = "SELECT * FROM " + TABLE_NAME + " C WHERE C." + ID_COLUMN + " = ?";

    public static final String SELECT_NULL_FOLDERS = "SELECT * FROM " + TABLE_NAME + " WHERE " + STORAGE_FOLDER_COLUMN + " is null";

    public static final String SELECT_BY_STATUS = "SELECT * FROM " + TABLE_NAME + " C WHERE C."
            + STATUS_COLUMN + " = ? ORDER BY C." + DOWNLOAD_DATE_COLUMN;

    public static final String SELECT_IN_DOWNLOAD_MANAGER = "SELECT * FROM " + TABLE_NAME + " C WHERE C."
            + STATUS_COLUMN + " in (?, ?) ORDER BY C." + STATUS_COLUMN + ", C."
            + DOWNLOAD_DATE_COLUMN;


    // SEARCH QUERIES "TOOLBOX"

    public static final String SELECT_DOWNLOADS_BASE = "SELECT C.*" +
            " FROM " + TABLE_NAME + " C WHERE C." + STATUS_COLUMN + " in (?, ?, ?) AND C."+SITE_COLUMN+" in (%1)";

    public static final String SELECT_DOWNLOADS_TITLE = " AND C." + TITLE_COLUMN + " like '%2' ";

    public static final String SELECT_DOWNLOADS_JOINS = " AND C." + ID_COLUMN
            + " in (" + "SELECT CA." + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM "
            + ContentAttributeTable.TABLE_NAME + " CA INNER JOIN " + AttributeTable.TABLE_NAME
            + " A ON CA." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " = A."+ AttributeTable.ID_COLUMN + " WHERE ";

    public static final String SELECT_DOWNLOADS_AUTHOR = "(lower(A." + AttributeTable.NAME_COLUMN + ") = '%3' AND A."
            + AttributeTable.TYPE_COLUMN + " in (0, 7))";

    public static final String SELECT_DOWNLOADS_TAGS = "(lower(A." + AttributeTable.NAME_COLUMN + ") in (%4) AND A."
            + AttributeTable.TYPE_COLUMN + " = 3)";

/*
    public static final String SELECT_DOWNLOADS = "SELECT C.*" +
            " FROM " + TABLE_NAME + " C WHERE C." + STATUS_COLUMN + " in (?, ?, ?) AND C."+SITE_COLUMN+" in (%1)" +
            " AND (C." + TITLE_COLUMN + " like ? OR C." + ID_COLUMN
            + " in (" + "SELECT CA." + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM "
            + ContentAttributeTable.TABLE_NAME + " CA INNER JOIN " + AttributeTable.TABLE_NAME
            + " A ON CA." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " = A."
            + AttributeTable.ID_COLUMN + " WHERE lower(A." + AttributeTable.NAME_COLUMN + ") like ? AND A."
            + AttributeTable.TYPE_COLUMN + " in (?, ?, ?)))";
*/
}
