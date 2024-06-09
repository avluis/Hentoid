package me.devsaki.hentoid.events

/**
 * Tracks download preparation events (parsing events) for interested subscribers.
 */
class DownloadPreparationEvent(
    val contentId: Long,  // ID of the corresponding content (<=0 if not defined)
    private val storedId: Long,  // Stored ID of the corresponding content (<=0 if not defined)
    val progress: Float // Progress
) {

    fun getRelevantId(): Long {
        return if (contentId < 1) storedId else contentId
    }

    fun isCompleted(): Boolean {
        return progress >= 1.0
    }
}