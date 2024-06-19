package me.devsaki.hentoid.notification.splitMerge

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.SplitMergeType

class SplitMergeCompleteNotification(
    private val books: Int,
    private val nbError: Int,
    private val type: SplitMergeType
) :
    BaseNotification() {
    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = if (nbError > 0) {
            if (SplitMergeType.SPLIT == type) R.string.merge_fail
            else R.string.split_fail
        } else {
            if (SplitMergeType.SPLIT == type) R.string.split_success
            else R.string.merge_success
        }
        val content = if (nbError > 0) context.resources.getQuantityString(
            R.plurals.notif_delete_fail_details,
            nbError,
            nbError
        )
        else context.resources.getQuantityString(
            R.plurals.notif_process_complete_details,
            books,
            books
        )

        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(title))
            .setContentText(content)
            .build()
    }
}
