package me.devsaki.hentoid.events;

public class UpdateEvent {
    public final boolean hasNewVersion;

    public UpdateEvent(boolean hasNewVersion)
    {
        this.hasNewVersion = hasNewVersion;
    }
}
