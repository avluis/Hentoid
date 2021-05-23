package me.devsaki.hentoid.events;

/**
 * Created by Robb on 2019/06/19
 * Tracks download preparation events for interested subscribers.
 */
public class DownloadPreparationEvent {
    public final long contentId;// ID of the corresponding content (<=0 if not defined)
    public final int done;      // Number of steps done
    public final int total;     // Total number of steps to do

    public DownloadPreparationEvent(final long contentId, int done, int total) {
        this.contentId = contentId;
        this.done = done;
        this.total = total;
    }

    public boolean isCompleted() {
        return (done == total);
    }
}
