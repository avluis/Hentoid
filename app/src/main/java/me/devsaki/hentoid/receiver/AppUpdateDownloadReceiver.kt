package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.devsaki.hentoid.core.runUpdateDownloadWorker
import me.devsaki.hentoid.workers.data.UpdateDownloadData
import timber.log.Timber

/**
 * Broadcast receiver for when an "update available" notification is tapped
 */
class AppUpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        intent?.extras?.let { extras ->
            val data = UpdateDownloadData.Parser(extras)
            val apkUrl = data.url
            context.runUpdateDownloadWorker(apkUrl)
        } ?: Timber.w("no data")
    }
}