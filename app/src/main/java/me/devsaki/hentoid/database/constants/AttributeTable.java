package me.devsaki.hentoid.database.constants;

import me.devsaki.hentoid.enums.StatusContent;

/**
 * Created by DevSaki on 10/05/2015.
 * db Attribute Table
 */
public abstract class AttributeTable {

    static final String TABLE_NAME = "attribute";

    static final String ID_COLUMN = "id";
    private static final String URL_COLUMN = "url";
    static final String NAME_COLUMN = "name";
    static final String TYPE_COLUMN = "type";

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID_COLUMN
            + " INTEGER PRIMARY KEY," + URL_COLUMN + " TEXT," + NAME_COLUMN + " TEXT" + ","
            + TYPE_COLUMN + " INTEGER" + ")";

    // INSERT
    public static final String INSERT_STATEMENT = "INSERT OR IGNORE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?);";

    // SELECT
    public static final String SELECT_BY_ID = "SELECT * FROM " + TABLE_NAME + " WHERE " + ID_COLUMN + " = ?";

    public static final String SELECT_ALL_BY_TYPE = "select distinct a." + ID_COLUMN + ", lower(a." + NAME_COLUMN + "), a." + URL_COLUMN + ", count(*) " +
            "from " + TABLE_NAME + " a inner join " + ContentAttributeTable.TABLE_NAME + " ca on a." + ID_COLUMN + " = ca." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " " +
            "inner join " + ContentTable.TABLE_NAME + " c on ca." + ContentAttributeTable.CONTENT_ID_COLUMN + "=c." + ContentTable.ID_COLUMN + " " +
            "where a." + TYPE_COLUMN + "=? and c." + ContentTable.STATUS_COLUMN + " in (" + StatusContent.DOWNLOADED.getCode() + "," + StatusContent.ERROR.getCode() + "," + StatusContent.MIGRATED.getCode() + ") ";

    public static final String SELECT_ALL_SOURCE_FILTER = " AND c." + ContentTable.SOURCE_COLUMN + " IN (%1) ";

    public static final String SELECT_ALL_ATTR_FILTER = " AND lower(a." + NAME_COLUMN + ") LIKE lower('%%2%') ";

    public static final String SELECT_ALL_BY_USAGE_FAVS = " and c." + ContentTable.FAVOURITE_COLUMN + " = 1 ";

    public static final String SELECT_ALL_BY_USAGE_TAG_FILTER = " AND (c." + ContentTable.ID_COLUMN + " in (" +
            " SELECT " + ContentAttributeTable.CONTENT_ID_COLUMN + " FROM (" +
            " select ca1." + ContentAttributeTable.CONTENT_ID_COLUMN + " , COUNT(*)" +
            " from " + ContentAttributeTable.TABLE_NAME + " as ca1 inner join " + AttributeTable.TABLE_NAME + " as a1 on ca1." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " = a1." + AttributeTable.ID_COLUMN +
            " where lower(a1." + AttributeTable.NAME_COLUMN + ") in (%2) GROUP BY 1 HAVING COUNT(*) = %3) ) ) ";

    public static final String SELECT_ALL_BY_USAGE_END = " group by 2 order by 3 desc, 2 asc";


    public static final String SELECT_COUNT_BY_TYPE_SELECT = "SELECT A." + TYPE_COLUMN + ", COUNT(DISTINCT(LOWER(A." + NAME_COLUMN + "))) FROM " + TABLE_NAME + " a " +
            " INNER JOIN " + ContentAttributeTable.TABLE_NAME + " ca ON a." + ID_COLUMN + " = ca." + ContentAttributeTable.ATTRIBUTE_ID_COLUMN +
            " INNER JOIN " + ContentTable.TABLE_NAME + " c ON ca." + ContentAttributeTable.CONTENT_ID_COLUMN + "=c." + ContentTable.ID_COLUMN +
            " WHERE c." + ContentTable.STATUS_COLUMN + " IN (" + StatusContent.DOWNLOADED.getCode() + "," + StatusContent.ERROR.getCode() + "," + StatusContent.MIGRATED.getCode() + ")";

    public static final String SELECT_COUNT_BY_TYPE_SOURCE_FILTER = SELECT_ALL_SOURCE_FILTER;

    public static final String SELECT_COUNT_BY_TYPE_ATTR_FILTER_JOINS = ContentTable.SELECT_DOWNLOADS_JOINS;
    public static final String SELECT_COUNT_BY_TYPE_ATTR_FILTER_ATTRS = ContentTable.SELECT_DOWNLOADS_TAGS;

    public static final String SELECT_COUNT_BY_TYPE_GROUP = " GROUP BY A." + TYPE_COLUMN;


    public static final String SELECT_COUNT_BY_SOURCE_SELECT = "SELECT C." + ContentTable.SOURCE_COLUMN + ", COUNT(*) FROM " + ContentTable.TABLE_NAME + " C " +
            " WHERE C." + ContentTable.STATUS_COLUMN + " IN (" + StatusContent.DOWNLOADED.getCode() + "," + StatusContent.ERROR.getCode() + "," + StatusContent.MIGRATED.getCode() + ")";

    public static final String SELECT_COUNT_BY_SOURCE_SOURCE_FILTER = SELECT_ALL_SOURCE_FILTER;

    public static final String SELECT_COUNT_BY_SOURCE_ATTR_FILTER_JOINS = ContentTable.SELECT_DOWNLOADS_JOINS;
    public static final String SELECT_COUNT_BY_SOURCE_ATTR_FILTER_ATTRS = ContentTable.SELECT_DOWNLOADS_TAGS;

    public static final String SELECT_COUNT_BY_SOURCE_GROUP = " GROUP BY C." + ContentTable.SOURCE_COLUMN;


    public static final String SELECT_BY_CONTENT_ID = "SELECT T." + ID_COLUMN + ", T." + URL_COLUMN
            + ", T." + NAME_COLUMN + ", T." + TYPE_COLUMN + " FROM " + TABLE_NAME + " T INNER JOIN "
            + ContentAttributeTable.TABLE_NAME + " C ON T." + ID_COLUMN + " = C."
            + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " WHERE C."
            + ContentAttributeTable.CONTENT_ID_COLUMN + " = ?";
}
