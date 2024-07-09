package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import me.devsaki.hentoid.R

/**
 * Broadcast receiver for the cancel button on split notifications
 */
class SplitNotificationCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).cancelUniqueWork(R.id.split_service.toString())
    }
}