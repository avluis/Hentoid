package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import me.devsaki.hentoid.util.Helper
import kotlin.math.abs

@Entity
data class DuplicateEntry(
    val referenceId: Long,
    val referenceSize: Long,
    var duplicateId: Long = -1,
    var duplicateSize: Long = -1,
    val titleScore: Float = 0f,
    val coverScore: Float = 0f,
    val artistScore: Float = 0f,
    @Id var id: Long = 0 // ID is mandatory for ObjectBox to work
) : Comparable<DuplicateEntry> {

    @Transient
    private var totalScore = -1f

    @Transient
    var nbDuplicates = 1

    @Transient
    var referenceContent: Content? = null

    @Transient
    var duplicateContent: Content? = null

    @Transient
    var keep: Boolean = true

    @Transient
    var isBeingDeleted: Boolean = false


    fun calcTotalScore(): Float {
        // Try to fetch pre-calculated score, if present
        if (totalScore > -1) return totalScore
        // Calculate
        val operands = ArrayList<Pair<Float, Float>>()
        if (titleScore > -1) operands.add(Pair(titleScore, 1f))
        if (coverScore > -1) operands.add(Pair(coverScore, 1f))
        return Helper.weightedAverage(operands) * (if (artistScore > -1) artistScore else 1f)
    }

    fun uniqueHash(): Long {
        return Helper.hash64(("$referenceId.$duplicateId").toByteArray())
    }

    override fun compareTo(other: DuplicateEntry): Int {
        if (referenceId == other.referenceId && duplicateId == other.duplicateId) return 0

        // If scores are within 0.01, they are considered equal
        val scoreDelta = abs(calcTotalScore() - other.calcTotalScore())
        if (scoreDelta >= 0.01) return calcTotalScore().compareTo(other.calcTotalScore())

        return duplicateSize.compareTo(other.duplicateSize)
    }
}