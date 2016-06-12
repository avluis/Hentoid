package me.devsaki.hentoid.dirpicker.util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 06/10/2016.
 * Event Bus for shared instance
 */
public class Bus {
    private static final String TAG = LogHelper.makeLogTag(Bus.class);

    public static void register(EventBus bus, Object listener) {
        try {
            bus.register(listener);
        } catch (EventBusException e) {
            LogHelper.w(TAG, e.toString());
        }
    }

    public static void unregister(EventBus bus, Object listener) {
        try {
            bus.unregister(listener);
        } catch (EventBusException e) {
            LogHelper.w(TAG, e.toString());
        }
    }
}
