package me.devsaki.hentoid.database.domains

import android.content.Context
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
        val achievements: List<Achievement> by lazy { init(HentoidApp.getInstance()) }

        fun init(context: Context): List<Achievement> {
            val result = ArrayList<Achievement>()

            context.resources.openRawResource(R.raw.achievements).use { `is` ->
                val achievementsStr = FileHelper.readStreamAsString(`is`)
                val achievementsObj = JsonHelper.jsonToObject(
                    achievementsStr,
                    JsonAchievements::class.java
                )
                achievementsObj.achievements.forEachIndexed { index, entry ->
                    val title = context.resources.getIdentifier(
                        "ach_name_$index",
                        "string",
                        context.packageName
                    )
                    val desc = context.resources.getIdentifier(
                        "ach_desc_$index",
                        "string",
                        context.packageName
                    )
                    result.add(
                        Achievement(
                            entry.id,
                            entry.type,
                            false,
                            title,
                            desc,
                            R.drawable.ic_achievement
                        )
                    )
                }
                result.add(
                    Achievement(
                        63,
                        Type.GOLD,
                        true,
                        R.string.ach_name_100,
                        R.string.ach_desc_100,
                        R.drawable.ic_warning // TODO special icon
                    )
                )
                result.add(
                    Achievement(
                        62,
                        Type.GOLD,
                        true,
                        R.string.ach_name_101,
                        R.string.ach_desc_101,
                        R.drawable.ic_warning // TODO special icon
                    )
                )
            }
            return result
        }
    }
}