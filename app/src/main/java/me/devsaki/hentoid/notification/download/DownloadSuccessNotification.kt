package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.receiver.DownloadNotificationDeleteReceiver
import me.devsaki.hentoid.util.notification.Notification

class DownloadSuccessNotification(private val completeCount: Int) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_hentoid)
            .setContentTitle(getTitle(context))
            .setContentIntent(getDefaultIntent(context))
            .setDeleteIntent(getDeleteIntent(context))
            .setLocalOnly(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    private fun getTitle(context: Context): String {
        return context.resources
            .getQuantityString(R.plurals.download_completed, completeCount, completeCount)
    }

    private fun getDefaultIntent(context: Context): PendingIntent {
        val resultIntent = Intent(context, LibraryActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        return PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getDeleteIntent(context: Context): PendingIntent {
        val intent = Intent(context, DownloadNotificationDeleteReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    }
}
