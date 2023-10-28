package me.devsaki.hentoid.events

/**
 * Tracks download preparation events (parsing events) for interested subscribers.
 */
class DownloadPreparationEvent(
    val contentId: Long,  // ID of the corresponding content (<=0 if not defined)
    private val storedId: Long,  // Stored ID of the corresponding content (<=0 if not defined)
    val done: Int, // Number of steps done
    val total: Int// Total number of steps to do
) {

    fun getRelevantId(): Long {
        return if (contentId < 1) storedId else contentId
    }

    fun isCompleted(): Boolean {
        return done == total
    }
}