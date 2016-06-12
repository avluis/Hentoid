package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Content Table
 */
public abstract class ContentTable {

    public static final String TABLE_NAME = "content";

    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?);";
    public static final String LIMIT_BY_PAGE = " LIMIT ?,?";
    private static final String ID_COLUMN = "id";
    public static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
            + ID_COLUMN + " = ?";
    private static final String UNIQUE_SITE_ID_COLUMN = "unique_site_id";
    private static final String CATEGORY_COLUMN = "category";
    private static final String URL_COLUMN = "url";
    private static final String TITLE_COLUMN = "title";
    public static final String ORDER_ALPHABETIC = " ORDER BY C." + TITLE_COLUMN;
    private static final String HTML_DESCRIPTION_COLUMN = "html_description";
    private static final String QTY_PAGES_COLUMN = "qty_pages";
    private static final String UPLOAD_DATE_COLUMN = "upload_date";
    private static final String DOWNLOAD_DATE_COLUMN = "download_date";
    public static final String ORDER_BY_DATE = " ORDER BY C." + DOWNLOAD_DATE_COLUMN + " DESC";
    private static final String STATUS_COLUMN = "status";
    public static final String UPDATE_CONTENT_DOWNLOAD_DATE_STATUS_STATEMENT = "UPDATE "
            + TABLE_NAME + " SET " + DOWNLOAD_DATE_COLUMN + " = ?, " + STATUS_COLUMN
            + " = ? WHERE " + ID_COLUMN + " = ?";
    public static final String UPDATE_CONTENT_STATUS_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
            + STATUS_COLUMN + " = ? WHERE " + STATUS_COLUMN + " = ?";
    private static final String COVER_IMAGE_URL_COLUMN = "cover_image_url";
    private static final String SITE_COLUMN = "site";
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + ID_COLUMN + " INTEGER PRIMARY KEY," + UNIQUE_SITE_ID_COLUMN + " TEXT,"
            + CATEGORY_COLUMN + " TEXT," + URL_COLUMN + " TEXT," + HTML_DESCRIPTION_COLUMN
            + " TEXT," + TITLE_COLUMN + " TEXT" + "," + QTY_PAGES_COLUMN + " INTEGER" + ","
            + UPLOAD_DATE_COLUMN + " INTEGER" + "," + DOWNLOAD_DATE_COLUMN + " INTEGER" + ","
            + STATUS_COLUMN + " INTEGER" + "," + COVER_IMAGE_URL_COLUMN + " TEXT"
            + "," + SITE_COLUMN + " INTEGER" + ")";
    public static final String SELECT_BY_CONTENT_ID = "SELECT " + ID_COLUMN + ", "
            + UNIQUE_SITE_ID_COLUMN + ", " + CATEGORY_COLUMN + ", " + URL_COLUMN + ", "
            + TITLE_COLUMN + ", " + HTML_DESCRIPTION_COLUMN + ", " + QTY_PAGES_COLUMN + ", "
            + UPLOAD_DATE_COLUMN + ", " + DOWNLOAD_DATE_COLUMN + ", " + STATUS_COLUMN + ", "
            + COVER_IMAGE_URL_COLUMN + ", " + SITE_COLUMN + " FROM " + TABLE_NAME + " C WHERE C."
            + ID_COLUMN + " = ?";
    public static final String SELECT_BY_STATUS = "SELECT " + ID_COLUMN + ", "
            + UNIQUE_SITE_ID_COLUMN + ", " + CATEGORY_COLUMN + ", " + URL_COLUMN + ", "
            + TITLE_COLUMN + ", " + HTML_DESCRIPTION_COLUMN + ", " + QTY_PAGES_COLUMN + ", "
            + UPLOAD_DATE_COLUMN + ", " + DOWNLOAD_DATE_COLUMN + ", " + STATUS_COLUMN + ", "
            + COVER_IMAGE_URL_COLUMN + ", " + SITE_COLUMN + " FROM " + TABLE_NAME + " C WHERE C."
            + STATUS_COLUMN + " = ? ORDER BY C." + DOWNLOAD_DATE_COLUMN;
    public static final String SELECT_IN_DOWNLOAD_MANAGER = "SELECT " + ID_COLUMN + ", "
            + UNIQUE_SITE_ID_COLUMN + ", " + CATEGORY_COLUMN + ", " + URL_COLUMN + ", "
            + TITLE_COLUMN + ", " + HTML_DESCRIPTION_COLUMN + ", " + QTY_PAGES_COLUMN + ", "
            + UPLOAD_DATE_COLUMN + ", " + DOWNLOAD_DATE_COLUMN + ", " + STATUS_COLUMN + ", "
            + COVER_IMAGE_URL_COLUMN + ", " + SITE_COLUMN + " FROM " + TABLE_NAME + " C WHERE C."
            + STATUS_COLUMN + " in (?, ?) ORDER BY C." + STATUS_COLUMN + ", C."
            + DOWNLOAD_DATE_COLUMN;
    public static final String SELECT_DOWNLOADS = "SELECT C." + ID_COLUMN + ", C."
            + UNIQUE_SITE_ID_COLUMN + ", C." + CATEGORY_COLUMN + ", C." + URL_COLUMN + ", C."
            + TITLE_COLUMN + ", C." + HTML_DESCRIPTION_COLUMN + ", C." + QTY_PAGES_COLUMN + ", C."
            + UPLOAD_DATE_COLUMN + ", C." + DOWNLOAD_DATE_COLUMN + ", C." + STATUS_COLUMN + ", C."
            + COVER_IMAGE_URL_COLUMN + ", C." + SITE_COLUMN + " FROM " + TABLE_NAME + " C WHERE C."
            + STATUS_COLUMN + " in (?, ?, ?) AND (C." + TITLE_COLUMN + " like ? OR C." + ID_COLUMN
            + " in (" + "SELECT CA." + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM "
            + ContentAttributeTable.TABLE_NAME + " CA INNER JOIN " + AttributeTable.TABLE_NAME
            + " A ON CA." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " = A."
            + AttributeTable.ID_COLUMN + " WHERE A." + AttributeTable.NAME_COLUMN + " like ? AND A."
            + AttributeTable.TYPE_COLUMN + " in (?, ?, ?)))";
}
