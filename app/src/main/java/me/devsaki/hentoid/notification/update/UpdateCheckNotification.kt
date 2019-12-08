package me.devsaki.hentoid.notification.update

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class UpdateCheckNotification : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
            .setDefaults(0)
            .setSmallIcon(R.drawable.ic_stat_hentoid)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVibrate(null)
            .setSound(null)
            .setContentTitle("Checking for updates")
            .setContentText("Please wait")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
}
