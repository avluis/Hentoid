package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.receiver.DownloadNotificationDeleteReceiver
import me.devsaki.hentoid.util.notification.BaseNotification

class DownloadSuccessNotification(private val completeCount: Int) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
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
        return getPendingIntentForActivity(context, LibraryActivity::class.java)
    }

    private fun getDeleteIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, DownloadNotificationDeleteReceiver::class.java)
    }
}
