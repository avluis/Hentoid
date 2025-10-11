package me.devsaki.hentoid.notification.download

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.receiver.DownloadNotificationCancelReceiver
import me.devsaki.hentoid.receiver.DownloadNotificationPauseReceiver
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import java.util.Locale

class DownloadProgressNotification() : BaseNotification() {
    var title = ""
    var progress = 0
    var max = 0
    var sizeDownloadedMB = 0
    var estimateBookSizeMB = 0
    var avgSpeedKbps = 0

    lateinit var builder: NotificationCompat.Builder

    override fun onCreateNotification(context: Context): android.app.Notification {
        val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)
        val total = if (estimateBookSizeMB > -1) "/$estimateBookSizeMB" else ""
        val message =
            context.getString(R.string.download_notif_speed, sizeDownloadedMB, total, avgSpeedKbps)

        if (!this::builder.isInitialized) {
            builder = NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_app)
                .setColor(context.getThemedColor(R.color.secondary_light))
                .setContentIntent(getDefaultIntent(context))
                .addAction(
                    R.drawable.ic_action_pause,
                    context.getString(R.string.pause),
                    getPauseIntent(context)
                )
                .addAction(
                    R.drawable.ic_cancel,
                    context.getString(R.string.cancel),
                    getCancelIntent(context)
                )
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
        }

        builder.apply {
            setContentTitle(title)
            setContentText(message)
            setContentInfo(progressString)
            setProgress(max, progress, 0 == max)
        }

        return builder.build()
    }

    private fun getDefaultIntent(context: Context): PendingIntent {
        return getPendingIntentForActivity(context, QueueActivity::class.java)
    }

    private fun getPauseIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, DownloadNotificationPauseReceiver::class.java)
    }

    private fun getCancelIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, DownloadNotificationCancelReceiver::class.java)
    }
}
