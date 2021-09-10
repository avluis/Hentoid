package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification
import java.util.*

class DeleteProgressNotification(
    private val title: String,
    private val progress: Int,
    private val max: Int,
    private val isPurge: Boolean
) : Notification {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, DeleteNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(if (isPurge) R.string.purge_progress else R.string.delete_progress))
            .setContentText(title)
            .setContentInfo(progressString)
            .setProgress(max, progress, false)
            .setColor(ThemeHelper.getColor(context, R.color.secondary_light))
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
