package me.devsaki.hentoid.events;

import androidx.annotation.Nullable;

public class CommunicationEvent {

    private final int type;
    private final int recipient;
    private final String message;

    public CommunicationEvent(int eventType, int recipient, @Nullable final String message) {
        this.type = eventType;
        this.recipient = recipient;
        this.message = message;
    }

    public int getType() {
        return type;
    }

    public int getRecipient() {
        return recipient;
    }

    public String getMessage() {
        return message;
    }
}
