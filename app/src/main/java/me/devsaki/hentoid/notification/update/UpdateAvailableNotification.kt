package me.devsaki.hentoid.notification.update

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.services.UpdateDownloadService
import me.devsaki.hentoid.util.PendingIntentCompat
import me.devsaki.hentoid.util.notification.Notification

class UpdateAvailableNotification(private val updateUrl: String) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val intent = UpdateDownloadService.makeIntent(context, updateUrl)

        val pendingIntent = PendingIntentCompat.getForegroundService(context, intent)

        return NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_hentoid_shape)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(longArrayOf(1, 1, 1))
                .setContentTitle(context.resources.getText(R.string.update_available))
                .setContentText(context.resources.getText(R.string.tap_to_download))
                .setContentIntent(pendingIntent)
                .build()
    }
}
