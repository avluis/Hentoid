package me.devsaki.hentoid.events;

/**
 * Created by Robb on 2019/06/19
 * Tracks download preparation events for interested subscribers.
 */
public class DownloadPreparationEvent {
    public final int done;      // Number of steps done
    public final int total;     // Total number of steps to do

    public DownloadPreparationEvent(int done, int total) {
        this.done = done;
        this.total = total;
    }

    public boolean isCompleted()
    {
        return (done == total);
    }
}
