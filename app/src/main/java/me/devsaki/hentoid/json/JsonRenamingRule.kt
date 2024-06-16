package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.enums.AttributeType

@JsonClass(generateAdapter = true)
data class JsonRenamingRule(
    val type: AttributeType,
    val sourceName: String,
    val targetName: String
) {
    constructor(data: RenamingRule) : this(data.attributeType, data.sourceName, data.targetName)

    fun toEntity(): RenamingRule {
        return RenamingRule(attributeType = type, sourceName = sourceName, targetName = targetName)
    }
}
