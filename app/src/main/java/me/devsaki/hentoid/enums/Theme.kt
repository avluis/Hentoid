package me.devsaki.hentoid.enums

enum class Theme(val id: Int, val resourceName: String) {
    // Warning : names are _resoure names_ that should match the xml files, not display names
    LIGHT(0, "Light"),

    // "Deep red" in settings
    DARK(1, "Dark"),

    // "Dark" in settings, not to be mistaken with usual "black" that means AMOLED
    BLACK(2, "Black"),

    // Fallback
    NONE(99, "Light");

    companion object {
        fun searchById(id: Int): Theme {
            for (s in Theme.values()) if (id == s.id) return s
            return Theme.NONE
        }
    }
}