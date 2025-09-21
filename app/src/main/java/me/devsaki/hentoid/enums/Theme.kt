package me.devsaki.hentoid.enums

import me.devsaki.hentoid.R

enum class Theme(val id: Int, val resourceName: String, val nameRes : Int) {
    // Warning : names are _resoure names_ that should match the xml files, not display names
    LIGHT(0, "Light", R.string.theme_light),

    // "Deep red" in settings
    DARK(1, "Dark", R.string.theme_deep_red),

    // "Dark" in settings, not to be mistaken with usual "black" that means AMOLED
    BLACK(2, "Black", R.string.theme_dark),

    // Material You
    YOU(3, "You", R.string.theme_you),

    // Fallback
    NONE(99, "Light", R.string.none);

    companion object {
        fun searchById(id: Int): Theme {
            for (s in entries) if (id == s.id) return s
            return Theme.NONE
        }
    }
}