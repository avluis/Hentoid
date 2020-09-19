package me.devsaki.hentoid.notification.archive

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class ArchiveCompleteNotification(private val books: Int, private val isError : Boolean) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ArchiveNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(if (isError)"Archival failed" else "Archival complete")
            .setContentText(if (isError)"At least one book failed to be archived" else "$books archived successfully")
            .build()
}
