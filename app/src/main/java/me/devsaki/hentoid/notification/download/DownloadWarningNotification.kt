package me.devsaki.hentoid.notification.download

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class DownloadWarningNotification(
    private val title: String,
    private val absolutePath: String
) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.download_notif_failed))
            .setStyle(getBigStyle(context))
            .setLocalOnly(true)
            .build()

    private fun getBigStyle(context: Context): NotificationCompat.BigTextStyle {
        return NotificationCompat.BigTextStyle()
            .bigText( context.getString(R.string.download_notif_failed_details, title, absolutePath))
    }
}
