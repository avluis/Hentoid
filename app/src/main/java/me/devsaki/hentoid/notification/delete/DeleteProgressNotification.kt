package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import java.util.Locale

class DeleteProgressNotification(
    private val title: String,
    private val progress: Int,
    private val max: Int,
    private val type: ProgressType
) : BaseNotification() {

    enum class ProgressType {
        DELETE_BOOKS, PURGE_BOOKS, DELETE_PAGES, STREAM_BOOKS
    }

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, DeleteNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(
                context.getString(
                    when (type) {
                        ProgressType.PURGE_BOOKS -> R.string.purge_progress
                        ProgressType.DELETE_PAGES -> R.string.delete_pages_progress
                        ProgressType.STREAM_BOOKS -> R.string.stream_progress
                        else -> R.string.delete_progress
                    }
                )
            )
            .setContentText(title)
            .setContentInfo(progressString)
            .setProgress(max, progress, false)
            .setColor(context.getThemedColor(R.color.secondary_light))
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
