package me.devsaki.hentoid.enums

import androidx.annotation.StringRes
import me.devsaki.hentoid.R

enum class Grouping(
    val id: Int,
    @StringRes val displayName: Int,
    val canReorderGroups: Boolean,
    val canDeleteGroups: Boolean,
    val canReorderBooks: Boolean
) {
    FLAT(0, R.string.groups_flat, false, false, false),
    ARTIST(1, R.string.groups_by_artist, false, true, true),
    DL_DATE(2, R.string.groups_by_dl_date, false, false, false),
    DYNAMIC(3, R.string.groups_dynamic, true, true, false),
    FOLDERS(97, R.string.groups_folders, false, true, false),
    CUSTOM(98, R.string.groups_custom, true, true, true),
    NONE(99, R.string.none, false, false, false);

    companion object {
        // Same as ValueOf with a fallback to NONE
        // (vital for forward compatibility)
        fun searchByName(name: String): Grouping {
            for (s in entries) if (s.name.equals(name, ignoreCase = true)) return s
            return NONE
        }

        fun searchById(id: Int): Grouping {
            for (s in entries) if (id == s.id) return s
            return NONE
        }
    }
}