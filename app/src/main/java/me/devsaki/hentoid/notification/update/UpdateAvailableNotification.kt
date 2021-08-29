package me.devsaki.hentoid.notification.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.AppUpdateDownloadReceiver
import me.devsaki.hentoid.util.notification.Notification
import me.devsaki.hentoid.workers.data.AppUpdateData

class UpdateAvailableNotification(private val downloadUrl: String) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val intent = Intent(context, AppUpdateDownloadReceiver::class.java)
        val builder = AppUpdateData.Builder().setUrl(downloadUrl)
        intent.putExtras(builder.bundle)
        val pendingIntent =
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)

        return NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(1, 1, 1))
            .setContentTitle(context.resources.getText(R.string.update_available))
            .setContentText(context.resources.getText(R.string.tap_to_download))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }
}
