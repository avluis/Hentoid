package me.devsaki.hentoid.notification.maintenance

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification

class MaintenanceNotification(private val title: String) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
            NotificationCompat.Builder(context, MaintenanceNotificationChannel.ID)
                    .setSmallIcon(R.drawable.ic_hentoid_shape)
                    .setContentTitle(context.getString(R.string.maintenance))
                    .setContentText(title)
                    .setColor(ThemeHelper.getColor(context, R.color.secondary_light))
                    .setLocalOnly(true)
                    .setOngoing(true)
                    .build()
}
