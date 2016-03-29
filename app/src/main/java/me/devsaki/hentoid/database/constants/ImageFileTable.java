package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 */
public abstract class ImageFileTable {

    public static final String TABLE_NAME = "image_file";

    public static final String ID_COLUMN = "id";
    public static final String CONTENT_ID_COLUMN = "content_id";
    public static final String ORDER_COLUMN = "order_file";
    public static final String STATUS_COLUMN = "status";
    public static final String URL_COLUMN = "url";
    public static final String NAME_COLUMN = "name";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID_COLUMN
            + " INTEGER PRIMARY KEY," + CONTENT_ID_COLUMN + " INTEGER," + ORDER_COLUMN + " INTEGER,"
            + URL_COLUMN + " TEXT" + "," + NAME_COLUMN + " TEXT" + "," + STATUS_COLUMN + " INTEGER"
            + ")";

    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?,?,?);";
    public static final String UPDATE_IMAGE_FILE_STATUS_STATEMENT = "UPDATE " + TABLE_NAME + " SET "
            + STATUS_COLUMN + " = ? WHERE " + ID_COLUMN + " = ?";
    public static final String DELETE_STATEMENT = "DELETE FROM " + TABLE_NAME + " WHERE "
            + CONTENT_ID_COLUMN + " = ?";

    public static final String SELECT_BY_CONTENT_ID = "SELECT " + ID_COLUMN + ", "
            + CONTENT_ID_COLUMN + ", " + ORDER_COLUMN + ", " + STATUS_COLUMN + ", " + URL_COLUMN
            + ", " + NAME_COLUMN + " FROM " + TABLE_NAME + " C WHERE C." + CONTENT_ID_COLUMN
            + " = ? ORDER BY " + ORDER_COLUMN;
}
