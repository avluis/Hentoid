package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.greenrobot.eventbus.EventBus;

import me.devsaki.hentoid.events.DownloadCommandEvent;

/**
 * Broadcast receiver for the cancel button on download notifications
 */
public class DownloadNotificationCancelReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        EventBus.getDefault().post(new DownloadCommandEvent(DownloadCommandEvent.Type.EV_CANCEL));
    }
}
