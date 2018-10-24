package me.devsaki.hentoid.events;

import me.devsaki.hentoid.database.domains.Content;

/**
 * Created by avluis on 06/10/2016.
 * Tracks downloads events for interested subscribers.
 */
public class DownloadEvent {
    public static final int EV_PROGRESS = 0;    // Download progress of current book (always one book at a time)
    public static final int EV_PAUSE = 1;       // Queue is paused
    public static final int EV_UNPAUSE = 2;     // Queue is unpaused
    public static final int EV_CANCEL = 3;      // One book has been "canceled" (ordered to be removed from the queue)
    public static final int EV_COMPLETE = 4;    // Current book download has been completed
    public static final int EV_SKIP = 5;        // Cancel without removing the Content; used when the 2nd book is prioritized to end up in the first place of the queue or when 1st book is deprioritized

    public final int eventType;                 // Event type (see constants EV_XXX above)
    public final Content content;               // Corresponding book (for EV_CANCEL events that are the only ones not concerning the 1st book of the queue + EV_COMPLETE to update the proper book in library view)
    public final int pagesOK;                   // Number of pages that have been downloaded successfully for current book
    public final int pagesKO;                   // Number of pages that have been downloaded with errors for current book
    public final int pagesTotal;                // Number of pages to download for current book

    /**
     * Use for EV_PROGRESS and EV_COMPLETE events
     *
     * @param content    progressing or completed content
     * @param eventType  event type code (among DownloadEvent public static EV_ values)
     * @param pagesOK    pages downloaded successfully
     * @param pagesKO    pages downloaded with errors
     * @param pagesTotal total pages to download
     */
    public DownloadEvent(Content content, int eventType, int pagesOK, int pagesKO, int pagesTotal) {
        this.content = content;
        this.eventType = eventType;
        this.pagesOK = pagesOK;
        this.pagesKO = pagesKO;
        this.pagesTotal = pagesTotal;
    }

    /**
     * Use for EV_CANCEL events
     *
     * @param content   Canceled content
     * @param eventType event type code (among DownloadEvent public static EV_ values)
     */
    public DownloadEvent(Content content, int eventType) {
        this.content = content;
        this.eventType = eventType;
        this.pagesOK = 0;
        this.pagesKO = 0;
        this.pagesTotal = 0;
    }

    /**
     * Use for EV_PAUSE, EV_UNPAUSE and EV_SKIP events
     *
     * @param eventType event type code (among DownloadEvent public static EV_ values)
     */
    public DownloadEvent(int eventType) {
        this.content = null;
        this.eventType = eventType;
        this.pagesOK = 0;
        this.pagesKO = 0;
        this.pagesTotal = 0;
    }
}
