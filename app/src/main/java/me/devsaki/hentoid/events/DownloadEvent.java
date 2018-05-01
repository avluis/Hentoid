package me.devsaki.hentoid.events;

import me.devsaki.hentoid.database.domains.Content;

/**
 * Created by avluis on 06/10/2016.
 * Tracks downloads events for interested subscribers.
 */
public class DownloadEvent {
    public static final int EV_PROGRESS = 0;
    public static final int EV_PAUSE = 1;
    public static final int EV_UNPAUSE = 2;
    public static final int EV_CANCEL = 3;
    public static final int EV_COMPLETE = 4;
    public static final int EV_SKIP = 5; // Cancel without destroying the Content; used when the 2nd book is prioritized and end up in the first place of the queue or when 1st book is deprioritized

    public final Content content;
    public final int eventType;
    public final int pagesOK;
    public final int pagesKO;
    public final int pagesTotal;

    public DownloadEvent(int eventType) {
        this.content = null; this.eventType = eventType; this.pagesOK = 0; this.pagesKO = 0; this.pagesTotal = 0;
    }
    public DownloadEvent(int eventType, int pagesOK, int pagesKO, int pagesTotal) {
        this.content = null; this.eventType = eventType; this.pagesOK = pagesOK; this.pagesKO = pagesKO; this.pagesTotal = pagesTotal;
    }
    public DownloadEvent(Content content, int eventType) {
        this.content = content; this.eventType = eventType; this.pagesOK = 0; this.pagesKO = 0; this.pagesTotal = 0;
    }

    @Deprecated
    public DownloadEvent(int eventType, double percent) {
        this.content = null; this.eventType = eventType; this.pagesOK = 0; this.pagesKO = 0; this.pagesTotal = 0;
    }
}
