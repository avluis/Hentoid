package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Attribute Table
 */
public abstract class AttributeTable {

    public static final String TABLE_NAME = "attribute";

    public static final String ID_COLUMN = "id";
    public static final String URL_COLUMN = "url";
    public static final String NAME_COLUMN = "name";
    public static final String TYPE_COLUMN = "type";

    // CREATE
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID_COLUMN
            + " INTEGER PRIMARY KEY," + URL_COLUMN + " TEXT," + NAME_COLUMN + " TEXT" + ","
            + TYPE_COLUMN + " INTEGER" + ")";

    // INSERT
    public static final String INSERT_STATEMENT = "INSERT OR IGNORE INTO " + TABLE_NAME
            + " VALUES (?,?,?,?);";

    // SELECT
    public static final String SELECT_ALL_BY_USAGE_BASE = "select distinct lower(a."+NAME_COLUMN+"), count(*) " +
            "from "+TABLE_NAME+" a inner join "+ContentAttributeTable.TABLE_NAME+" ca on a."+ID_COLUMN+" = ca."+ContentAttributeTable.ATTRIBUTE_ID_COLUMN+" " +
            "inner join "+ContentTable.TABLE_NAME+" c on ca."+ContentAttributeTable.CONTENT_ID_COLUMN+"=c."+ContentTable.ID_COLUMN+" "+
            "where a."+TYPE_COLUMN+"=? and c."+ContentTable.STATUS_COLUMN+" in (1,4,5) and c."+ContentTable.SITE_COLUMN+" in (%1)";

    public static final String SELECT_ALL_BY_USAGE_TAG_FILTER = " AND (c."+ContentTable.ID_COLUMN+" in (" +
            "select ca1."+ContentAttributeTable.CONTENT_ID_COLUMN+
            " from "+ContentAttributeTable.TABLE_NAME+" as ca1 inner join "+AttributeTable.TABLE_NAME+" as a1 on ca1."+ContentAttributeTable.ATTRIBUTE_ID_COLUMN+" = a1."+AttributeTable.ID_COLUMN+
            " where a1."+AttributeTable.TYPE_COLUMN+"=3 and a1."+AttributeTable.NAME_COLUMN+" in (%2) ) ) ";

    public static final String SELECT_ALL_BY_USAGE_END = " group by 1 order by 2 desc, 1 asc";

    public static final String SELECT_BY_CONTENT_ID = "SELECT T." + ID_COLUMN + ", T." + URL_COLUMN
            + ", T." + NAME_COLUMN + ", T." + TYPE_COLUMN + " FROM " + TABLE_NAME + " T INNER JOIN "
            + ContentAttributeTable.TABLE_NAME + " C ON T." + ID_COLUMN + " = C."
            + ContentAttributeTable.ATTRIBUTE_ID_COLUMN + " WHERE C."
            + ContentAttributeTable.CONTENT_ID_COLUMN + " = ?";
}
