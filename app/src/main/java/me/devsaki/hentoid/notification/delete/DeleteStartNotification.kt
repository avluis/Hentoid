package me.devsaki.hentoid.notification.delete

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class DeleteStartNotification(
    private val max: Int,
    private val isPurge: Boolean
) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, DeleteNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setProgress(max, 0, 0 == max)
            .setContentTitle(context.getString(if (isPurge) R.string.purge_progress else R.string.delete_progress))
            .setContentText("")
            .build()
}
