package me.devsaki.hentoid.notification.splitMerge

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.SplitMergeType

class SplitMergeStartNotification(
    private val max: Int,
    private val type: SplitMergeType
) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setProgress(max, 0, 0 == max)
            .setContentTitle(context.getString(if (SplitMergeType.SPLIT == type) R.string.split_progress else R.string.merge_progress))
            .setContentText("")
            .build()
}
