package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Image File Table
 */
public abstract class ImageFileTable {

    public static final String TABLE_NAME = "image_file";

    private static final String ID_COLUMN = "id";
    private static final String CONTENT_ID_COLUMN = "content_id";
    private static final String ORDER_COLUMN = "order_file";
    private static final String STATUS_COLUMN = "status";
    private static final String URL_COLUMN = "url";
    private static final String NAME_COLUMN = "name";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID_COLUMN
            + " INTEGER PRIMARY KEY," + CONTENT_ID_COLUMN + " INTEGER," + ORDER_COLUMN + " INTEGER,"
            + URL_COLUMN + " TEXT" + "," + NAME_COLUMN + " TEXT" + "," + STATUS_COLUMN + " INTEGER"
            + ")";

    // INSERT
    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?,?,?);";

    // DELETE
    public static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
            + CONTENT_ID_COLUMN + " = ?";

    // UDPATE
    public static final String UPDATE_IMAGE_FILE_STATUS_FROM_ID = "UPDATE " + TABLE_NAME + " SET " + STATUS_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?";
    public static final String UPDATE_IMAGE_FILE_STATUS_FROM_ID_AND_STATUS = "UPDATE " + TABLE_NAME + " SET " + STATUS_COLUMN + " = ? WHERE " + CONTENT_ID_COLUMN + " = ? AND "+STATUS_COLUMN +" = ?";

    // SELECT
    public static final String SELECT_BY_CONTENT_ID = "SELECT " + ID_COLUMN + ", "
            + CONTENT_ID_COLUMN + ", " + ORDER_COLUMN + ", " + STATUS_COLUMN + ", " + URL_COLUMN
            + ", " + NAME_COLUMN + " FROM " + TABLE_NAME + " C WHERE C." + CONTENT_ID_COLUMN
            + " = ? ORDER BY " + ORDER_COLUMN;

    public static final String SELECT_PROCESSED_BY_CONTENT_ID = "SELECT " + STATUS_COLUMN + ", COUNT(*) FROM " + TABLE_NAME + " WHERE " + CONTENT_ID_COLUMN + " = ? GROUP BY 1";
    // Corresponding index
    public static final String SELECT_PROCESSED_BY_CONTENT_ID_IDX  = "CREATE INDEX image_file_content ON " + TABLE_NAME + " (" + CONTENT_ID_COLUMN + ", " + STATUS_COLUMN + ")";
}
