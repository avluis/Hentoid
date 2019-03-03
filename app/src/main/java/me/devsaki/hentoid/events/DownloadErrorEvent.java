package me.devsaki.hentoid.events;

import me.devsaki.hentoid.database.domains.ErrorRecord;

/**
 * Created by Robb on 03/2019
 * Tracks downloads error events for interested subscribers.
 */
public class DownloadErrorEvent {
    public final ErrorRecord error;

    public DownloadErrorEvent(ErrorRecord e) {
        this.error = e;
    }
}
