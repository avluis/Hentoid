package me.devsaki.hentoid.events

import me.devsaki.hentoid.database.domains.Content

/**
 * Tracks downloads events for interested subscribers.
 */
class DownloadEvent {

    companion object {
        /**
         * Use for EV_PREPARATION event
         *
         * @param step    step code for the event
         * @param content Content that is being downloaded; null if inapplicable
         */
        fun fromPreparationStep(step: Step, content: Content?): DownloadEvent {
            return DownloadEvent(Type.EV_PREPARATION, Motive.NONE, step, content)
        }

        /**
         * Use for EV_PAUSE event
         *
         * @param motive motive code for the event
         */
        fun fromPauseMotive(motive: Motive): DownloadEvent {
            return DownloadEvent(Type.EV_PAUSED, motive, Step.NONE, 0)
        }

        fun fromPauseMotive(motive: Motive, spaceLeftBytes: Long): DownloadEvent {
            return DownloadEvent(Type.EV_PAUSED, motive, Step.NONE, spaceLeftBytes)
        }
    }

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
        REMOVE_DUPLICATE
    }

    var eventType = Type.EV_NONE // Event type (see constants EV_XXX above)

    var content: Content? =
        null // Corresponding book (for EV_CANCEL events that are the only ones not concerning the 1st book of the queue + EV_COMPLETE to update the proper book in library view)

    var pagesOK = 0 // Number of pages that have been downloaded successfully for current book

    var pagesKO = 0 // Number of pages that have been downloaded with errors for current book

    var pagesTotal = 0 // Number of pages to download for current book

    var downloadedSizeB: Long = 0 // Total size of downloaded content (bytes)

    var motive = Motive.NONE // Motive for certain events (EV_PAUSE)

    var step = Step.NONE // Step for EV_PREPARATION

    var log = ""

    /**
     * Use for EV_PROGRESS and EV_COMPLETE events
     *
     * @param content    progressing or completed content
     * @param eventType  event type code (among DownloadEvent public static EV_ values)
     * @param pagesOK    pages downloaded successfully
     * @param pagesKO    pages downloaded with errors
     * @param pagesTotal total pages to download
     */
    constructor(
        content: Content,
        eventType: Type,
        pagesOK: Int,
        pagesKO: Int,
        pagesTotal: Int,
        downloadedSizeB: Long
    ) {
        this.content = content
        this.eventType = eventType
        this.pagesOK = pagesOK
        this.pagesKO = pagesKO
        this.pagesTotal = pagesTotal
        this.downloadedSizeB = downloadedSizeB
        motive = Motive.NONE
        step = Step.NONE
    }

    /**
     * Use for EV_CANCEL events
     *
     * @param content   Canceled content
     * @param eventType event type code (among DownloadEvent public static EV_ values)
     */
    constructor(content: Content, eventType: Type) {
        this.content = content
        this.eventType = eventType
        pagesOK = 0
        pagesKO = 0
        pagesTotal = 0
        downloadedSizeB = 0
        motive = Motive.NONE
        step = Step.NONE
    }

    /**
     * Use for EV_PAUSE event
     *
     * @param eventType      event type code (among DownloadEvent public static EV_ values)
     * @param motive         motive for the event
     * @param step           step for the event
     * @param spaceLeftBytes space left on device
     */
    constructor(
        eventType: Type,
        motive: Motive,
        step: Step,
        spaceLeftBytes: Long
    ) {
        content = null
        this.eventType = eventType
        pagesOK = 0
        pagesKO = 0
        pagesTotal = 0
        downloadedSizeB = spaceLeftBytes
        this.motive = motive
        this.step = step
    }

    /**
     * Use for EV_PREPARATION event
     *
     * @param eventType event type code (among DownloadEvent public static EV_ values)
     * @param motive    motive for the event
     * @param step      step for the event
     * @param content   Content that is being downloaded; null if inapplicable
     */
    constructor(
        eventType: Type,
        motive: Motive,
        step: Step,
        content: Content?
    ) {
        this.content = content
        this.eventType = eventType
        pagesOK = 0
        pagesKO = 0
        pagesTotal = 0
        downloadedSizeB = 0
        this.motive = motive
        this.step = step
    }

    /**
     * Use for EV_PAUSE, EV_UNPAUSE and EV_SKIP events
     *
     * @param eventType event type code (among DownloadEvent public static EV_ values)
     */
    constructor(eventType: Type) {
        content = null
        this.eventType = eventType
        pagesOK = 0
        pagesKO = 0
        pagesTotal = 0
        downloadedSizeB = 0
        motive = Motive.NONE
        step = Step.NONE
    }

    constructor(commandEvent: DownloadCommandEvent) {
        content = commandEvent.content
        eventType = fromCommandEventType(commandEvent.type)
        pagesOK = 0
        pagesKO = 0
        pagesTotal = 0
        downloadedSizeB = 0
        motive = Motive.NONE
        step = Step.NONE
    }

    private fun fromCommandEventType(code: DownloadCommandEvent.Type): Type {
        return when (code) {
            DownloadCommandEvent.Type.EV_PAUSE -> Type.EV_PAUSED
            DownloadCommandEvent.Type.EV_UNPAUSE -> Type.EV_UNPAUSED
            DownloadCommandEvent.Type.EV_CANCEL -> Type.EV_CANCELED
            DownloadCommandEvent.Type.EV_SKIP -> Type.EV_SKIPPED
            DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT -> Type.EV_CONTENT_INTERRUPTED
        }
    }

    fun getNumberRetries(): Int {
        return content?.numberDownloadRetries ?: 0
    }

    private fun setLog(log: String): DownloadEvent {
        this.log = log
        return this
    }
}