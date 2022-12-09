package me.devsaki.hentoid.notification.updateJson

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class UpdateJsonStartNotification : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, UpdateJsonNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setProgress(1, 1, true)
            .setContentTitle(context.resources.getString(R.string.notif_json_progress))
            .setContentText(context.resources.getString(R.string.notif_json_progress))
            .build()
}
