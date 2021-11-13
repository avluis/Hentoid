package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.greenrobot.eventbus.EventBus;

import me.devsaki.hentoid.events.DownloadEvent;

/**
 * Broadcast receiver for the pause button on download notifications
 */
public class DownloadNotificationPauseReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.Type.EV_PAUSE));
    }
}
