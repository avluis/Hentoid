package me.devsaki.hentoid.events;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.devsaki.hentoid.database.domains.Content;

/**
 * Send downloads commands for download worker and parsers
 */
public class DownloadCommandEvent {

    @IntDef({Type.EV_PAUSE, Type.EV_UNPAUSE, Type.EV_CANCEL, Type.EV_SKIP, Type.EV_INTERRUPT_CONTENT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
        int EV_PAUSE = 1; // Queue is paused
        int EV_UNPAUSE = 2; // Queue is unpaused
        int EV_CANCEL = 3; // One book has been "canceled" (ordered to be removed from the queue)
        int EV_SKIP = 5; // Cancel without removing the Content; used when the 2nd book is prioritized to end up in the first place of the queue or when 1st book is deprioritized
        // /!\ Using EV_SKIP without moving the position of the book won't have any effect
        int EV_INTERRUPT_CONTENT = 7; // Interrupt extra page parsing only for a specific Content
    }

    public final @Type
    int eventType;                 // Event type (see constants EV_XXX above)
    public final Content content;               // Corresponding book (for EV_CANCEL events that are the only ones not concerning the 1st book of the queue + EV_COMPLETE to update the proper book in library view)
    public String log = "";

    public DownloadCommandEvent(@NonNull Content content, @Type int eventType) {
        this.content = content;
        this.eventType = eventType;
    }

    public DownloadCommandEvent(@Type int eventType) {
        this.content = null;
        this.eventType = eventType;
    }

    private DownloadCommandEvent setLog(@NonNull String log) {
        this.log = log;
        return this;
    }
}
