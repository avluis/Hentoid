package me.devsaki.hentoid.events;

import androidx.annotation.NonNull;

/**
 * Created by Robb on 2019/06/19
 * Tracks download preparation events for interested subscribers.
 */
public class DownloadPreparationEvent {
    public final String url;    // URL where this takes place
    public final int done;      // Number of steps done
    public final int total;     // Total number of steps to do

    public DownloadPreparationEvent(@NonNull final String url, int done, int total) {
        this.url = url;
        this.done = done;
        this.total = total;
    }

    public boolean isCompleted()
    {
        return (done == total);
    }
}
