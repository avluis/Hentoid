package me.devsaki.hentoid.dirpicker.util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

import timber.log.Timber;

/**
 * Created by avluis on 06/10/2016.
 * Event Bus for shared instance
 */
public class Bus {

    public static void register(EventBus bus, Object listener) {
        try {
            bus.register(listener);
        } catch (EventBusException e) {
            Timber.w(e);
        }
    }

    public static void unregister(EventBus bus, Object listener) {
        try {
            bus.unregister(listener);
        } catch (EventBusException e) {
            Timber.w(e);
        }
    }
}
