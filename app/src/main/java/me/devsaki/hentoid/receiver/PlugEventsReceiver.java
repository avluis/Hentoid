package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

import org.greenrobot.eventbus.EventBus;

import me.devsaki.hentoid.events.CommunicationEvent;
import timber.log.Timber;

public class PlugEventsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!isInitialStickyBroadcast()) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Timber.d("USB plugged");
                EventBus.getDefault().post(new CommunicationEvent(CommunicationEvent.EV_BROADCAST, CommunicationEvent.RC_ALL, "usb"));
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                Timber.d("Power plugged");
                EventBus.getDefault().post(new CommunicationEvent(CommunicationEvent.EV_BROADCAST, CommunicationEvent.RC_ALL, "power"));
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                Timber.d("Audio plugged");
                EventBus.getDefault().post(new CommunicationEvent(CommunicationEvent.EV_BROADCAST, CommunicationEvent.RC_ALL, "audio"));
            }
        }
    }
}
