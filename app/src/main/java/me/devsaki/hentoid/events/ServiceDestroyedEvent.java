package me.devsaki.hentoid.events;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ServiceDestroyedEvent {
    @IntDef({Service.DOWNLOAD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Service {
        int DOWNLOAD = 0; // Download service
    }

    public final @Service
    int service;

    public ServiceDestroyedEvent(@Service int service) {
        this.service = service;
    }
}
