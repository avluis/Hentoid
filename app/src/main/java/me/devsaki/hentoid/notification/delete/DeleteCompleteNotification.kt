package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.BaseDeleteWorker

class DeleteCompleteNotification(
    private val books: Int,
    private val nbError: Int,
    private val operation: BaseDeleteWorker.Operation
) :
    BaseNotification() {
    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = if (nbError > 0) {
            if (operation == BaseDeleteWorker.Operation.STREAM) R.string.notif_stream_fail
            else if (operation == BaseDeleteWorker.Operation.PURGE) R.string.notif_delete_prepurge_fail
            else R.string.notif_delete_fail
        } else {
            if (operation == BaseDeleteWorker.Operation.STREAM) R.string.notif_stream_complete
            else if (operation == BaseDeleteWorker.Operation.PURGE) R.string.notif_delete_prepurge_complete
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

        return NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(title))
            .setContentText(content)
            .build()
    }
}
