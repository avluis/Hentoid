package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import me.devsaki.hentoid.R

/**
 * Broadcast receiver for the stop button on duplicate detector notifications
 */
class DuplicateNotificationStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(R.id.duplicate_detector_service.toString())
    }
}