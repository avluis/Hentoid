package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;

/**
 * App themes
 */
public enum Theme {

    LIGHT(0, "Light"),
    DARK(1, "Deep red"),
    BLACK(2, "Dark"),
    NONE(99, "None");

    private final int id;
    private final String name;

    Theme(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
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
