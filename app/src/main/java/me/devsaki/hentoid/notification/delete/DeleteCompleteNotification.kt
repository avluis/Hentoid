package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class DeleteCompleteNotification(private val books: Int, private val isError : Boolean) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, DeleteNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(if (isError)"Delete failed" else "Delete complete")
            .setContentText(if (isError)"At least one book failed to be deleted" else "$books deleted successfully")
            .build()
}
