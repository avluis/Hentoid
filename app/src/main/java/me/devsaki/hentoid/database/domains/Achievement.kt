package me.devsaki.hentoid.database.domains

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.json.core.JsonAchievements
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.file.FileHelper

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
        val achievements: Map<Int, Achievement> by lazy { init(HentoidApp.getInstance()) }

        fun init(context: Context): Map<Int, Achievement> {
            val result = HashMap<Int, Achievement>()

            context.resources.openRawResource(R.raw.achievements).use { `is` ->
                val achievementsStr = FileHelper.readStreamAsString(`is`)
                val achievementsObj = JsonHelper.jsonToObject(
                    achievementsStr,
                    JsonAchievements::class.java
                )
                achievementsObj.achievements.forEach { entry ->
                    val id = entry.id
                    val title = context.resources.getIdentifier(
                        "ach_name_$id",
                        "string",
                        context.packageName
                    )
                    val desc = context.resources.getIdentifier(
                        "ach_desc_$id",
                        "string",
                        context.packageName
                    )
                    result[entry.id] = Achievement(
                        entry.id,
                        entry.type,
                        false,
                        title,
                        desc,
                        R.drawable.ic_achievement
                    )
                }
                result[63] = Achievement(
                    63,
                    Type.GOLD,
                    true,
                    R.string.ach_name_62,
                    R.string.ach_desc_62,
                    R.drawable.ic_warning // TODO special icon
                )
                result[62] = Achievement(
                    62,
                    Type.GOLD,
                    true,
                    R.string.ach_name_63,
                    R.string.ach_desc_63,
                    R.drawable.ic_warning // TODO special icon
                )
            }
            return result
        }

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