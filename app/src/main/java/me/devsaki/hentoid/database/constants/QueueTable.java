package me.devsaki.hentoid.database.constants;

import me.devsaki.hentoid.enums.StatusContent;

/**
 * Created by Robb_w on 2018/04
 * db Queue table
 */
public abstract class QueueTable {

    private static final String TABLE_NAME = "queue";

    public static final String INSERT_STATEMENT = "INSERT OR REPLACE INTO " + TABLE_NAME + " VALUES (?,?);";

    // COLUMN NAMES
    private static final String ID_COLUMN = "content_id";
    private static final String ORDER_COLUMN = "rank";

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + ID_COLUMN + " INTEGER PRIMARY KEY, " + ORDER_COLUMN + " INTEGER DEFAULT 0)";

    // SELECT
    public static final String SELECT_QUEUE = "SELECT * FROM " + TABLE_NAME + " C ORDER BY " + ORDER_COLUMN + " ASC";

    // QUEUE MIGRATION AD HOC QUERY
    public static final String SELECT_CONTENT_FOR_QUEUE_MIGRATION = "SELECT * FROM " + ContentTable.TABLE_NAME + " "
            + "WHERE " + ContentTable.STATUS_COLUMN + " IN (" + StatusContent.DOWNLOADING.getCode() + "," + StatusContent.PAUSED.getCode() + ") "
            + "AND " + ContentTable.ID_COLUMN + " NOT IN ( SELECT " + ID_COLUMN + " FROM " + TABLE_NAME + ") "
            + "ORDER BY " + ContentTable.STATUS_COLUMN + "," + ContentTable.DOWNLOAD_DATE_COLUMN;
}
