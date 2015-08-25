package me.devsaki.hentoid.database.enums;

/**
 * Created by DevSaki on 10/05/2015.
 */
public enum AttributeType {

    ARTIST(0, "Artist"), PUBLISHER(1, "Publisher"), LANGUAGE(2, "Language"), TAG(3, "Tag"), TRANSLATOR(4, "Translator"), SERIE(5, "Serie"), UPLOADER(6, "Uploader"), CIRCLE(7, "Circle"), CHARACTER(8, "Character"), CATEGORY(9, "Category");

    private final int code;
    private final String description;

    AttributeType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AttributeType searchByCode(int code) {

        for (AttributeType s : AttributeType.values()) {
            if (s.getCode() == code)
                return s;
        }

        return null;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
