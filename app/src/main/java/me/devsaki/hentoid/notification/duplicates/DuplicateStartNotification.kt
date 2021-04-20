package me.devsaki.hentoid.notification.duplicates

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class DuplicateStartNotification : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
            NotificationCompat.Builder(context, DuplicateNotificationChannel.ID)
                    .setSmallIcon(R.drawable.ic_hentoid_shape)
                    .setProgress(1, 1, true)
                    .setContentTitle(context.resources.getString(R.string.duplicate_notif_start))
                    .build()
}
