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
            .setSmallIcon(R.drawable.ic_stat_hentoid_warning)
            .setContentTitle("Warning : download failed")
            .setStyle(getBigStyle())
            .setLocalOnly(true)
            .build()

    private fun getBigStyle(): NotificationCompat.BigTextStyle {
        return NotificationCompat.BigTextStyle()
            .bigText("Cannot download $title : unable to create folder $absolutePath. Please check your Hentoid folder and retry downloading using the (!) button.")
    }
}
