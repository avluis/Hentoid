package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.AttributeType.AttributeTypeConverter
import me.devsaki.hentoid.enums.AttributeType.UNDEFINED
import java.util.Objects

@Entity
data class RenamingRule(
    @Id
    var id: Long = 0,
    @Index
    @Convert(converter = AttributeTypeConverter::class, dbType = Int::class)
    val attributeType: AttributeType,
    @Index
    var sourceName: String,
    var targetName: String
) {
    @Transient
    var leftPart: String = ""

    @Transient
    var rightPart: String = ""


    constructor() : this(UNDEFINED, "", "")

    constructor(type: AttributeType, sourceName: String, targetName: String) : this(
        0,
        type,
        sourceName,
        targetName
    )


    fun doesMatchSourceName(name: String): Boolean {
        val starIndex = sourceName.indexOf('*')
        if (-1 == starIndex) return sourceName.equals(name, ignoreCase = true)
        else {
            var result = true
            val strLower = name.lowercase()
            if (leftPart.isNotEmpty()) result = strLower.startsWith(leftPart)
            if (rightPart.isNotEmpty()) result = result and strLower.endsWith(rightPart)
            return result
        }
    }

    fun getTargetName(name: String): String {
        if (!targetName.contains("*")) return targetName

        val starIndex = sourceName.indexOf('*')
        if (-1 == starIndex) return targetName
        else {
            var sourceWildcard = name
            if (leftPart.isNotEmpty()) sourceWildcard = sourceWildcard.substring(leftPart.length)
            if (rightPart.isNotEmpty()) sourceWildcard =
                sourceWildcard.substring(0, sourceWildcard.length - rightPart.length - 1)
            return targetName.replaceFirst("\\*".toRegex(), sourceWildcard)
        }
    }

    fun computeParts() {
        val starIndex = sourceName.indexOf('*')
        if (starIndex > -1) {
            leftPart = sourceName.substring(0, starIndex).lowercase()
            rightPart = if ((starIndex < sourceName.length - 1)) sourceName.substring(
                starIndex + 1,
                sourceName.length - 1
            ).lowercase() else ""
        }
    }

    override fun toString(): String {
        return "$attributeType:$sourceName=>$targetName"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as RenamingRule
        return attributeType == that.attributeType && sourceName == that.sourceName && targetName == that.targetName
    }

    override fun hashCode(): Int {
        return Objects.hash(attributeType, sourceName, targetName)
    }
}