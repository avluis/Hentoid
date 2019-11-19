package me.devsaki.hentoid.events;

import java.io.File;

import me.devsaki.hentoid.database.domains.Content;

/**
 * Created by Robb on 17/10/2018.
 * Tracks import events for interested subscribers.
 */
public class ImportEvent {
    public static final int EV_PROGRESS = 0;    // Import progress (1 book done)
    public static final int EV_COMPLETE = 1;    // Import complete

    public final int eventType;                 // Event type (see constants EV_XXX above)
    private final Content content;               // Corresponding book (for EV_PROGRESS)
    public final int booksOK;                   // Number of pages that have been downloaded successfully for current book
    public final int booksKO;                   // Number of pages that have been downloaded with errors for current book
    public final int booksTotal;                // Number of pages to download for current book
    public final File logFile;                  // Log file, if exists (for EV_COMPLETE)

    /**
     * Use for EV_PROGRESS events
     *
     * @param content    progressing content
     * @param eventType  event type code (among DownloadEvent public static EV_ values)
     * @param booksOK    pages downloaded successfully
     * @param booksKO    pages downloaded with errors
     * @param booksTotal total pages to download
     */
    public ImportEvent(int eventType, Content content, int booksOK, int booksKO, int booksTotal) {
        this.eventType = eventType;
        this.content = content;
        this.booksOK = booksOK;
        this.booksKO = booksKO;
        this.booksTotal = booksTotal;
        this.logFile = null;
    }

    /**
     * Use for EV_COMPLETE events
     *
     * @param eventType  event type code (among DownloadEvent public static EV_ values)
     * @param booksOK    pages downloaded successfully
     * @param booksKO    pages downloaded with errors
     * @param booksTotal total pages to download
     */
    public ImportEvent(int eventType, int booksOK, int booksKO, int booksTotal, File logFile) {
        this.content = null;
        this.eventType = eventType;
        this.booksOK = booksOK;
        this.booksKO = booksKO;
        this.booksTotal = booksTotal;
        this.logFile = logFile;
    }

}
