package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

import androidx.annotation.ArrayRes;

import org.greenrobot.eventbus.EventBus;

import java.util.Random;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.events.CommunicationEvent;

public class PlugEventsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!HentoidApp.isInForeground()) return;
        String action = intent.getAction();
        if (!isInitialStickyBroadcast()) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) || Intent.ACTION_POWER_CONNECTED.equals(action)) {
                EventBus.getDefault().post(
                        new CommunicationEvent(CommunicationEvent.EV_BROADCAST, CommunicationEvent.RC_ALL, getRandomQuoteFrom(context, R.array.power_reactions))
                );
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action) && intent.getIntExtra("state", 0) > 0) { // "Connect" event
                EventBus.getDefault().post(
                        new CommunicationEvent(CommunicationEvent.EV_BROADCAST, CommunicationEvent.RC_ALL, getRandomQuoteFrom(context, R.array.audio_reactions))
                );
            }
        }
    }

    private String getRandomQuoteFrom(Context context, @ArrayRes int res) {
        String[] quotes = context.getResources().getStringArray(res);
        return quotes[new Random().nextInt(quotes.length)];
    }
}
