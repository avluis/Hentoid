package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;

/**
 * App themes
 */
public enum Theme {

    // Warning : names are _resoure names_ that should match the xml files, not display names
    LIGHT(0, "Light"),
    DARK(1, "Dark"), // "Deep red" in settings
    BLACK(2, "Black"), // "Dark" in settings, not to be mistaken with usual "black" that means AMOLED
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


    public static Theme searchById(int id) {
        for (Theme s : values())
            if (id == s.id) return s;

        return NONE;
    }
}
