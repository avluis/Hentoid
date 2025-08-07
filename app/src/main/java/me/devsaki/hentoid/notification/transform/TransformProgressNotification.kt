package me.devsaki.hentoid.notification.transform

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.TransformNotificationStopReceiver
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import java.util.Locale

class TransformProgressNotification(
    var processedItems: Int,
    var maxItems: Int,
    var progress: Float,
) : BaseNotification() {
    lateinit var builder: NotificationCompat.Builder

    override fun onCreateNotification(context: Context): android.app.Notification {
        val progressPc: String = " %.2f%%".format(Locale.US, progress)
        val progressStr = if (0 == maxItems) "" else " ($processedItems / $maxItems)"

        if (!this::builder.isInitialized) {
            builder = NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_hentoid_shape)
                .setContentText("")
                .setColor(context.getThemedColor(R.color.secondary_light))
                .addAction(
                    R.drawable.ic_cancel,
                    context.getString(R.string.stop),
                    getStopIntent(context)
                )
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
        }

        builder.apply {
            setContentTitle(context.getString(R.string.transform_progress) + progressStr)
            setContentInfo(progressPc)
            setProgress(100, (progress * 100).toInt(), 0 == maxItems)
        }
        return builder.build()
    }

    private fun getStopIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, TransformNotificationStopReceiver::class.java)
    }
}
