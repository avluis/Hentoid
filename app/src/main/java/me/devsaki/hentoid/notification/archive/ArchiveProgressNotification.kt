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
    var progress: Float,
) : BaseNotification() {

    lateinit var builder: NotificationCompat.Builder

    override fun onCreateNotification(context: Context): android.app.Notification {
        val progressString: String = " %.2f%%".format(Locale.US, progress * 100)

        if (!this::builder.isInitialized) {
            builder = NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_hentoid_shape)
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
            setContentTitle(context.getString(R.string.archive_progress) + " : " + progressString)
            setContentText(progressString)
            setContentInfo(progressString)
            setProgress(100, (progress * 100).toInt(), false)
        }
        return builder.build()
    }

    private fun getStopIntent(context: Context): PendingIntent {
        return getPendingIntentForAction(context, ArchiveNotificationStopReceiver::class.java)
    }
}
