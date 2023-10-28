package me.devsaki.hentoid.events

import me.devsaki.hentoid.database.domains.Content

/**
 * Send downloads commands for download worker and parsers
 */
class DownloadCommandEvent(val type: Type, val content: Content? = null) {

    // content = corresponding book (for EV_CANCEL events that are the only ones not concerning the 1st book of the queue + EV_COMPLETE to update the proper book in library view)

    enum class Type {
        EV_PAUSE,   // Queue is paused
        EV_UNPAUSE, // Queue is unpaused
        EV_CANCEL,  // One book has been "canceled" (ordered to be removed from the queue)
        EV_SKIP,    // Cancel without removing the Content; used when the 2nd book is prioritized to end up in the first place of the queue or when 1st book is deprioritized

        // /!\ Using EV_SKIP without moving the position of the book won't have any effect
        EV_INTERRUPT_CONTENT // Interrupt extra page parsing only for a specific Content
    }

    var log = ""

    private fun setLog(log: String): DownloadCommandEvent {
        this.log = log
        return this
    }
}