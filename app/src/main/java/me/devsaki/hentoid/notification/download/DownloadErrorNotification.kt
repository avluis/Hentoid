package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.receiver.DownloadNotificationDeleteReceiver
import me.devsaki.hentoid.util.notification.Notification

class DownloadErrorNotification : Notification {

    private val content: Content?

    constructor() {
        this.content = null
    }

    constructor(content: Content) {
        this.content = content
    }

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.download_error))
            .setContentText(content?.title)
            .setContentIntent(getDefaultIntent(context))
            .setDeleteIntent(getDeleteIntent(context))
            .setLocalOnly(true)
            .setAutoCancel(true)
            .build()

    private fun getDefaultIntent(context: Context): PendingIntent {
        val resultIntent = Intent(context, LibraryActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, 0, resultIntent, flags)
    }

    private fun getDeleteIntent(context: Context): PendingIntent {
        val intent = Intent(context, DownloadNotificationDeleteReceiver::class.java)
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_CANCEL_CURRENT
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
