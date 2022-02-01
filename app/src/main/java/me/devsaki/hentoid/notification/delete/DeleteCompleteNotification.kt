package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class DeleteCompleteNotification(private val books: Int, private val isError: Boolean) :
    Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = if (isError) R.string.notif_delete_fail else R.string.notif_delete_complete
        val content = if (isError) context.getString(R.string.notif_delete_fail_details)
        else context.resources.getQuantityString(
            R.plurals.notif_delete_complete_details,
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
