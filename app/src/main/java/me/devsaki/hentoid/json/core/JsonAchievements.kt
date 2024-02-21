package me.devsaki.hentoid.json.core

import me.devsaki.hentoid.database.domains.Achievement

data class JsonAchievements(
    val achievements: List<JsonAchievement>
) {
    data class JsonAchievement(
        val id: Int,
        var type: Achievement.Type
    )
}
