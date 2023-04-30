package me.devsaki.hentoid.notification.transform

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification
import java.util.Locale

class TransformProgressNotification(
    private val progress: Int,
    private val max: Int
) : Notification {

    private val progressPc: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)
    private val progressStr = if (0 == max) "" else "($progress / $max)"

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, TransformNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.transform_progress) + progressStr)
            .setContentText("")
            .setContentInfo(progressPc)
            .setProgress(max, progress, false)
            .setColor(ThemeHelper.getColor(context, R.color.secondary_light))
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
