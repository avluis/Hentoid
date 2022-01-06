package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;

/**
 * Groupings
 */
public enum Grouping {

    FLAT(0, "Flat", false, false, false),
    ARTIST(1, "By artist", false, true, true),
    DL_DATE(2, "By download date", false, false, false),
    CUSTOM(98, "Custom", true, true, true),
    NONE(99, "None", false, false, false);

    private final int id;
    private final String name;
    private final boolean canReorderGroups;
    private final boolean canDeleteGroups;
    private final boolean canReorderBooks;

    Grouping(int id, @NonNull String name, boolean canReorderGroups, boolean canDeleteGroups, boolean canReorderBooks) {
        this.id = id;
        this.name = name;
        this.canReorderGroups = canReorderGroups;
        this.canDeleteGroups = canDeleteGroups;
        this.canReorderBooks = canReorderBooks;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean canDeleteGroups() {
        return canDeleteGroups;
    }

    public boolean canReorderGroups() {
        return canReorderGroups;
    }

    public boolean canReorderBooks() {
        return canReorderBooks;
    }


    // Same as ValueOf with a fallback to NONE
    // (vital for forward compatibility)
    public static Grouping searchByName(String name) {
        for (Grouping s : values())
            if (s.name().equalsIgnoreCase(name)) return s;

        return NONE;
    }

    public static Grouping searchById(int id) {
        for (Grouping s : values())
            if (id == s.id) return s;

        return NONE;
    }
}
