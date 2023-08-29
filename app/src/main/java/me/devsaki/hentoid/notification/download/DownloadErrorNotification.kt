package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.receiver.DownloadNotificationDeleteReceiver
import me.devsaki.hentoid.util.notification.BaseNotification

class DownloadErrorNotification(private val content: Content) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.download_error))
            .setContentText(content.title)
            .setContentIntent(getDefaultIntent(context))
            .setDeleteIntent(getDeleteIntent(context))
            .setLocalOnly(true)
            .setAutoCancel(true)
            .build()

    private fun getDefaultIntent(context: Context): PendingIntent {
        return getPendingIntentForActivity(context, LibraryActivity::class.java)
    }

    private fun getDeleteIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, DownloadNotificationDeleteReceiver::class.java)
    }
}
