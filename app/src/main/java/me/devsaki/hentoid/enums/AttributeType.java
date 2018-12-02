package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import me.devsaki.hentoid.R;

/**
 * Created by DevSaki on 10/05/2015.
 * Attribute Type enumerator
 */
public enum AttributeType {

    // Attributes stored in Attributes table of the DB
    ARTIST(0, R.drawable.ic_attribute_artist),
    PUBLISHER(1, R.drawable.ic_menu_fakku),
    LANGUAGE(2, R.drawable.ic_attribute_language),
    TAG(3, R.drawable.ic_attribute_tag),
    TRANSLATOR(4, R.drawable.ic_menu_fakku),
    SERIE(5, R.drawable.ic_attribute_serie),
    UPLOADER(6, R.drawable.ic_menu_fakku),
    CIRCLE(7, R.drawable.ic_menu_fakku),
    CHARACTER(8, R.drawable.ic_attribute_character),
    CATEGORY(9, R.drawable.ic_menu_fakku),
    // Attributes displayed on screen and stored elsewhere
    SOURCE(10, R.drawable.ic_attribute_source);

    private final int code;
    private final int icon;

    AttributeType(int code, int icon)
    {
        this.code = code;
        this.icon = icon;
    }

    @Nullable
    public static AttributeType searchByCode(int code) {
        for (AttributeType s : AttributeType.values()) {
            if (s.getCode() == code) {
                return s;
            }
        }

        return null;
    }

    @Nullable
    public static AttributeType searchByName(String name) {
        for (AttributeType s : AttributeType.values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }

        return null;
    }

    public int getCode() {
        return code;
    }

    public int getIcon() {
        return icon;
    }
}
