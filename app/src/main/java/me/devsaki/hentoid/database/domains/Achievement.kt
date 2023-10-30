package me.devsaki.hentoid.database.domains

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import me.devsaki.hentoid.R

data class Achievement(
    val id: Int,
    val type: Type,
    val himitsu: Boolean,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int
) {
    enum class Type {
        BRONZE,
        SILVER,
        GOLD
    }

    companion object {
        @ColorInt
        fun colorFromType(type: Type): Int {
            return when (type) {
                Type.BRONZE -> R.color.bronze
                Type.SILVER -> R.color.silver
                Type.GOLD -> R.color.gold
            }
        }
    }
}