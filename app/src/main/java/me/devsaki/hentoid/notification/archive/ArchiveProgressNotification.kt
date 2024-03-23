package me.devsaki.hentoid.notification.archive

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.ArchiveNotificationStopReceiver
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import java.util.Locale

class ArchiveProgressNotification(
    private val title: String,
    processedItems: Int,
    maxItems: Int,
    private val progress: Float,
) : BaseNotification() {

    private val progressPc: String = " %.2f%%".format(Locale.US, progress)
    private val progressStr = if (0 == maxItems) "" else " ($processedItems / $maxItems)"

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, ArchiveNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.archive_progress) + progressStr)
            .setContentText(progressPc)
            .setContentInfo(progressPc)
            .setProgress(100, (progress * 100).toInt(), false)
            .setColor(context.getThemedColor(R.color.secondary_light))
            .addAction(
                R.drawable.ic_cancel,
                context.getString(R.string.stop),
                getStopIntent(context)
            )
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun getStopIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, ArchiveNotificationStopReceiver::class.java)
    }
}
