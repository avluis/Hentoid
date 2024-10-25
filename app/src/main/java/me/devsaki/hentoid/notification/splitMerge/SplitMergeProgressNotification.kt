package me.devsaki.hentoid.notification.splitMerge

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.MergeNotificationCancelReceiver
import me.devsaki.hentoid.receiver.SplitNotificationCancelReceiver
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.SplitMergeType
import java.util.Locale

class SplitMergeProgressNotification(
    private val title: String,
    var progress: Int,
    private val max: Int,
    private val type: SplitMergeType
) : BaseNotification() {

    lateinit var builder: NotificationCompat.Builder

    override fun onCreateNotification(context: Context): android.app.Notification {
        val progressString: String = " %.2f%%".format(Locale.US, progress * 100.0 / max)

        if (!this::builder.isInitialized) {
            builder = NotificationCompat.Builder(context, ID)
                .setSmallIcon(R.drawable.ic_hentoid_shape)
                .setContentTitle(
                    context.getString(
                        when (type) {
                            SplitMergeType.SPLIT -> R.string.split_progress
                            SplitMergeType.MERGE -> R.string.merge_progress
                            SplitMergeType.REORDER -> R.string.reorder_progress
                        }
                    )
                )
                .setContentText(title)
                .setColor(context.getThemedColor(R.color.secondary_light))
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    R.drawable.ic_cancel,
                    context.getString(R.string.cancel),
                    getCancelIntent(context)
                )
        }

        builder.apply {
            setContentInfo(progressString)
            setProgress(max, progress, false)
        }
        return builder.build()
    }

    private fun getCancelIntent(context: Context): PendingIntent {
        return if (type == SplitMergeType.SPLIT)
            getPendingIntentForAction(context, SplitNotificationCancelReceiver::class.java)
        else getPendingIntentForAction(context, MergeNotificationCancelReceiver::class.java)
    }
}
