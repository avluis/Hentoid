package me.devsaki.hentoid.notification.archive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.ArchiveNotificationSuccessReceiver
import me.devsaki.hentoid.util.notification.Notification

class ArchiveCompleteNotification(private val books: Int, private val isError: Boolean) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, ArchiveNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_hentoid_shape)
                .setContentTitle(if (isError) "Archival failed" else "Archival complete")
                .setContentText(if (isError) "At least one book failed to be archived" else "$books book(s) archived successfully")
                .setContentIntent(getIntent(context))
                .build()
    }

    private fun getIntent(context: Context): PendingIntent {
        val intent = Intent(context, ArchiveNotificationSuccessReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    }
}
