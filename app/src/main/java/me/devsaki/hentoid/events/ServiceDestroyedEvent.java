package me.devsaki.hentoid.events;

import androidx.annotation.IdRes;

public class ServiceDestroyedEvent {
    public final @IdRes
    int service;

    public ServiceDestroyedEvent(@IdRes int service) {
        this.service = service;
    }
}
