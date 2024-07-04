package me.devsaki.hentoid.notification.splitMerge

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.SplitMergeType
import java.util.Locale

class SplitMergeProgressNotification(
    private val title: String,
    private val progress: Int,
    private val max: Int,
    private val type: SplitMergeType
) : BaseNotification() {

    private val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(
                context.getString(
                    when (type) {
                        SplitMergeType.SPLIT -> R.string.split_progress
                        SplitMergeType.MERGE -> R.string.merge_progress
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
