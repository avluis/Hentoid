package me.devsaki.hentoid.events;

/**
 * Created by avluis on 06/10/2016.
 * Tracks downloads events for interested subscribers.
 */
public class DownloadEvent {
    public final Double percent;

    public DownloadEvent(Double percent) {
        this.percent = percent;
    }
}
