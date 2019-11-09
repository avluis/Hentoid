package me.devsaki.hentoid.notification.maintenance

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class MaintenanceNotification(private val title: String) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, MaintenanceNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_hentoid)
            .setContentTitle(context.getString(R.string.maintenance))
            .setContentText(title)
            .setColor(ContextCompat.getColor(context, R.color.secondary))
            .setLocalOnly(true)
            .setOngoing(true)
            .build()
}
