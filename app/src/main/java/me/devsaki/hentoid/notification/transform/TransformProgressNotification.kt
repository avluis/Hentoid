package me.devsaki.hentoid.notification.transform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.TransformNotificationStopReceiver
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification
import java.util.Locale

class TransformProgressNotification(
    processedItems: Int,
    private val maxItems: Int,
    private val progress: Float,
) : Notification {

    private val progressPc: String = " %.2f%%".format(Locale.US, progress)
    private val progressStr = if (0 == maxItems) "" else " ($processedItems / $maxItems)"

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, TransformNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.transform_progress) + progressStr)
            .setContentText("")
            .setContentInfo(progressPc)
            .setProgress(100, (progress * 100).toInt(), 0 == maxItems)
            .setColor(ThemeHelper.getColor(context, R.color.secondary_light))
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
        val intent = Intent(context, TransformNotificationStopReceiver::class.java)
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_CANCEL_CURRENT
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
