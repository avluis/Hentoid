package me.devsaki.hentoid.notification.import_

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.notification.Notification
import java.util.*

class ImportProgressNotification(
        private val title: String,
        private val progress: Int,
        private val max: Int
) : Notification {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, ImportNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle(context.getString(R.string.importing_library))
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
