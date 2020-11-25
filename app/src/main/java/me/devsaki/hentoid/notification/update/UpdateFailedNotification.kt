package me.devsaki.hentoid.notification.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.services.UpdateDownloadService
import me.devsaki.hentoid.util.PendingIntentCompat
import me.devsaki.hentoid.util.notification.Notification

class UpdateFailedNotification(private val downloadUri: Uri) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val intent = Intent(context, UpdateDownloadService::class.java)
        intent.data = downloadUri

        val pendingIntent = PendingIntentCompat.getForegroundService(context, intent)

        return NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_hentoid_shape)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(longArrayOf(1, 1, 1))
                .setContentTitle(context.resources.getText(R.string.update_download_failed))
                .setContentText(context.resources.getText(R.string.tap_to_retry))
                .setContentIntent(pendingIntent)
                .build()
    }
}
