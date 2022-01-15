package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;

/**
 * App themes
 */
public enum Theme {

    // Warning : names are _resoure names_ that should match the xml files, not display names
    LIGHT(0, "Light"),
    DARK(1, "Dark"),
    BLACK(2, "Black"),
    NONE(99, "Light");

    private final int id;
    private final String resourceName;

    Theme(int id, @NonNull String resourceName) {
        this.id = id;
        this.resourceName = resourceName;
    }

    public int getId() {
        return id;
    }

    public String getResourceName() {
        return resourceName;
    }


    // Same as ValueOf with a fallback to NONE
    // (vital for forward compatibility)
    public static Theme searchByName(String name) {
        for (Theme s : values())
            if (s.name().equalsIgnoreCase(name)) return s;

        return NONE;
    }

    public static Theme searchById(int id) {
        for (Theme s : values())
            if (id == s.id) return s;

        return NONE;
    }
}
