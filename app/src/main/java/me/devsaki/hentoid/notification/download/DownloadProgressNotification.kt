package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.receiver.DownloadNotificationPauseReceiver
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification
import java.util.*

class DownloadProgressNotification(
    private val title: String,
    private val progress: Int,
    private val max: Int,
    private val sizeDownloadedMB: Int,
    private val estimateBookSizeMB: Int,
    private val avgSpeedKbps: Int
) : Notification {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        val total = if (estimateBookSizeMB > -1) "/$estimateBookSizeMB" else ""
        val message =
            context.getString(R.string.download_notif_speed, sizeDownloadedMB, total, avgSpeedKbps)

        return NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(title)
            .setContentText(message)
            .setContentInfo(progressString)
            .setProgress(max, progress, false)
            .setColor(ThemeHelper.getColor(context, R.color.secondary_light))
            .setContentIntent(getDefaultIntent(context))
            .addAction(
                R.drawable.ic_action_pause,
                context.getString(R.string.pause),
                getPauseIntent(context)
            )
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun getDefaultIntent(context: Context): PendingIntent {
        val resultIntent = Intent(context, QueueActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, 0, resultIntent, flags)
    }

    private fun getPauseIntent(context: Context): PendingIntent {
        val intent = Intent(context, DownloadNotificationPauseReceiver::class.java)
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_CANCEL_CURRENT
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
