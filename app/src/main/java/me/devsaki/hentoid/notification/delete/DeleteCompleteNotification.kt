package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class DeleteCompleteNotification(
    private val books: Int,
    private val nbError: Int,
    private val isDownloadPrepurge: Boolean
) :
    BaseNotification() {
    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = if (nbError > 0) {
            if (isDownloadPrepurge) R.string.notif_delete_prepurge_fail
            else R.string.notif_delete_fail
        } else {
            if (isDownloadPrepurge) R.string.notif_delete_prepurge_complete
            else R.string.notif_delete_complete
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

        return NotificationCompat.Builder(context, DeleteNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(title))
            .setContentText(content)
            .build()
    }
}
