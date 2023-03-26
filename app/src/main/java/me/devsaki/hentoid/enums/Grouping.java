package me.devsaki.hentoid.enums;

import androidx.annotation.StringRes;

import me.devsaki.hentoid.R;

/**
 * Groupings
 */
public enum Grouping {

    FLAT(0, R.string.groups_flat, false, false, false),
    ARTIST(1, R.string.groups_by_artist, false, true, true),
    DL_DATE(2, R.string.groups_by_dl_date, false, false, false),
    DYNAMIC(3, R.string.groups_dynamic, true, true, false),
    CUSTOM(98, R.string.groups_custom, true, true, true),
    NONE(99, R.string.none, false, false, false);

    private final int id;
    private final @StringRes
    int name;
    private final boolean canReorderGroups;
    private final boolean canDeleteGroups;
    private final boolean canReorderBooks;

    Grouping(int id, @StringRes int name, boolean canReorderGroups, boolean canDeleteGroups, boolean canReorderBooks) {
        this.id = id;
        this.name = name;
        this.canReorderGroups = canReorderGroups;
        this.canDeleteGroups = canDeleteGroups;
        this.canReorderBooks = canReorderBooks;
    }

    public int getId() {
        return id;
    }

    public @StringRes
    int getName() {
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
