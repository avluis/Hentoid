package me.devsaki.hentoid.events;

import androidx.annotation.Nullable;

public class CommunicationEvent {

    private final int type;
    private final String message;

    public CommunicationEvent(int eventType, @Nullable String message) {
        this.type = eventType;
        this.message = message;
    }

    public int getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
