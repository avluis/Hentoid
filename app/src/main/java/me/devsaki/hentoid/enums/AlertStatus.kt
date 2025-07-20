package me.devsaki.hentoid.enums

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import me.devsaki.hentoid.R

/**
 * Site issues alert levels
 */
enum class AlertStatus(@param:ColorRes val color: Int, @param:DrawableRes val icon: Int) {
    ORANGE(R.color.orange, R.drawable.ic_exclamation),
    RED(R.color.red, R.drawable.ic_error),
    GREY(R.color.dark_gray, R.drawable.ic_warning),
    BLACK(R.color.black, R.drawable.ic_nuclear),
    NONE(R.color.white, R.drawable.ic_info);

    companion object {
        // Same as ValueOf with a fallback to NONE
        // (vital for forward compatibility)
        fun searchByName(name: String): AlertStatus {
            for (s in entries) if (s.name.equals(name, ignoreCase = true)) return s
            return NONE
        }
    }
}