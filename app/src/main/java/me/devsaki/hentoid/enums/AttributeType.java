package me.devsaki.hentoid.enums;

/**
 * Created by DevSaki on 10/05/2015.
 * Attribute Type enumerator
 */
public enum AttributeType {

    ARTIST(0), PUBLISHER(1), LANGUAGE(2), TAG(3),
    TRANSLATOR(4), SERIE(5), UPLOADER(6), CIRCLE(7),
    CHARACTER(8), CATEGORY(9);

    private final int code;

    AttributeType(int code) {
        this.code = code;
    }

    public static AttributeType searchByCode(int code) {
        for (AttributeType s : AttributeType.values()) {
            if (s.getCode() == code) {
                return s;
            }
        }

        return null;
    }

    public int getCode() {
        return code;
    }
}