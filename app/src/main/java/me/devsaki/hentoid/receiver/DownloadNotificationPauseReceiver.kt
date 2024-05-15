package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.devsaki.hentoid.events.DownloadCommandEvent
import org.greenrobot.eventbus.EventBus

/**
 * Broadcast receiver for the pause button on download notifications
 */
class DownloadNotificationPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        EventBus.getDefault().post(DownloadCommandEvent(DownloadCommandEvent.Type.EV_PAUSE, null))
    }
}