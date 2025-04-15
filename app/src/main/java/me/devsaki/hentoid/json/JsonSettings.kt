package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.RenamingRule

@JsonClass(generateAdapter = true)
class JsonSettings {
    var settings: Map<String, Any> = HashMap()
    var renamingRules: List<JsonRenamingRule> = ArrayList()

    fun getEntityRenamingRules(): List<RenamingRule> {
        return renamingRules.map { it.toEntity() }
    }

    fun replaceRenamingRules(data: List<RenamingRule>) {
        this.renamingRules = data.map { JsonRenamingRule(it) }
    }
}