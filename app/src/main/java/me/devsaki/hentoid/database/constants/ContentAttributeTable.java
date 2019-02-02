package me.devsaki.hentoid.database.constants;

/**
 * Created by DevSaki on 10/05/2015.
 * db Content Attribute Table
 */
public abstract class ContentAttributeTable {

    static final String TABLE_NAME = "content_attribute";

    static final String CONTENT_ID_COLUMN = "content_id";
    static final String ATTRIBUTE_ID_COLUMN = "attribute_id";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
            + CONTENT_ID_COLUMN + " INTEGER," + ATTRIBUTE_ID_COLUMN + " INTEGER" + ")";
}
