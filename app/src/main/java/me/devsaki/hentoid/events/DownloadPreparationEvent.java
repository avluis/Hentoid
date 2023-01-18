package me.devsaki.hentoid.events;

/**
 * Tracks download preparation events (parsing events) for interested subscribers.
 */
public class DownloadPreparationEvent {
    public final long contentId;// ID of the corresponding content (<=0 if not defined)
    public final long storedId; // Stored ID of the corresponding content (<=0 if not defined)
    public final int done;      // Number of steps done
    public final int total;     // Total number of steps to do

    public DownloadPreparationEvent(long contentId, long storedId, int done, int total) {
        this.contentId = contentId;
        this.storedId = storedId;
        this.done = done;
        this.total = total;
    }

    public long getRelevantId() {
        return (contentId < 1) ? storedId : contentId;
    }

    public boolean isCompleted() {
        return (done == total);
    }
}
