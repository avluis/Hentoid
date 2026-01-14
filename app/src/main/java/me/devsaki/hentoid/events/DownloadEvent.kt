package me.devsaki.hentoid.events

import me.devsaki.hentoid.database.domains.Content

/**
 * Tracks downloads events for interested subscribers.
 */
class DownloadEvent(
    val eventType: Type = Type.EV_NONE, // Event type (see constants EV_XXX above)

    // Corresponding book (for EV_CANCEL events that are the only ones not concerning the 1st book of the queue + EV_COMPLETE to update the proper book in library view)
    val content: Content? = null,

    val pagesOK: Int = 0, // Number of pages that have been downloaded successfully for current book

    val pagesKO: Int = 0, // Number of pages that have been downloaded with errors for current book

    val pagesTotal: Int = 0, // Number of pages to download for current book

    val downloadedSizeB: Long = 0, // Total size of downloaded content (bytes)

    val fileDownloadProgress: Float = -1f, // File download progression, when downloading large files (e.g. archives)

    val motive: Motive = Motive.NONE, // Motive for certain events (EV_PAUSE)

    val step: Step = Step.NONE, // Step for EV_PREPARATION

    val log: String = ""
) {
    enum class Type {
        EV_NONE,
        EV_PROGRESS,    // Download progress of current book (always one book at a time)
        EV_PAUSED,      // Queue is paused
        EV_UNPAUSED,    // Queue is unpaused
        EV_CANCELED,    // One book has been "canceled" (ordered to be removed from the queue)
        EV_COMPLETE,    // Current book download has been completed
        EV_SKIPPED,     // Cancel without removing the Content; used when the 2nd book is prioritized to end up in the first place of the queue or when 1st book is deprioritized

        // /!\ Using EV_SKIP without moving the position of the book won't have any effect
        EV_PREPARATION,    // Informative event for the UI during preparation phase
        EV_CONTENT_INTERRUPTED
    }

    enum class Motive {
        NONE,
        NO_INTERNET,
        NO_WIFI,
        NO_STORAGE,
        NO_DOWNLOAD_FOLDER,
        DOWNLOAD_FOLDER_NOT_FOUND,
        DOWNLOAD_FOLDER_NO_CREDENTIALS,
        STALE_CREDENTIALS,
        NO_AVAILABLE_DOWNLOADS
    }

    enum class Step {
        NONE,
        INIT,
        PROCESS_IMG,
        FETCH_IMG,
        PREPARE_FOLDER,
        PREPARE_DOWNLOAD,
        SAVE_QUEUE,
        WAIT_PURGE,
        START_DOWNLOAD,
        COMPLETE_DOWNLOAD,
        REMOVE_DUPLICATE,
        ENCODE_ANIMATION // Specific to Pixiv (ugoira -> animation)
    }

    constructor(eventType: Type) : this(content = null, eventType = eventType)

    constructor(commandEvent: DownloadCommandEvent) : this(
        content = commandEvent.content,
        eventType = fromCommandEventType(commandEvent.type)
    )

    fun getNumberRetries(): Int {
        return content?.numberDownloadRetries ?: 0
    }

    companion object {
        /**
         * Use for EV_PREPARATION event
         *
         * @param step    step code for the event
         * @param content Content that is being downloaded; null if inapplicable
         */
        fun fromPreparationStep(step: Step, content: Content? = null): DownloadEvent {
            return DownloadEvent(
                eventType = Type.EV_PREPARATION,
                step = step,
                content = content
            )
        }

        /**
         * Use for EV_PAUSE event
         *
         * @param motive motive code for the event
         */
        fun fromPauseMotive(motive: Motive, spaceLeftBytes: Long = 0): DownloadEvent {
            return DownloadEvent(
                eventType = Type.EV_PAUSED,
                motive = motive,
                downloadedSizeB = spaceLeftBytes
            )
        }

        fun fromCommandEventType(code: DownloadCommandEvent.Type): Type {
            return when (code) {
                DownloadCommandEvent.Type.EV_PAUSE -> Type.EV_PAUSED
                DownloadCommandEvent.Type.EV_UNPAUSE -> Type.EV_UNPAUSED
                DownloadCommandEvent.Type.EV_CANCEL -> Type.EV_CANCELED
                DownloadCommandEvent.Type.EV_SKIP -> Type.EV_SKIPPED
                DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT -> Type.EV_CONTENT_INTERRUPTED
                DownloadCommandEvent.Type.EV_RESET_REQUEST_QUEUE -> Type.EV_NONE // Technical event; no use to inform user
            }
        }
    }
}