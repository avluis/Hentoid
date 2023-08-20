package me.devsaki.hentoid.notification.archive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.ArchiveNotificationSuccessReceiver
import me.devsaki.hentoid.util.notification.Notification

class ArchiveCompleteNotification(private val books: Int, private val nbErrors: Int) :
    Notification {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val title =
            if (nbErrors > 0) R.string.notif_archive_fail else R.string.notif_archive_complete
        val contentTxt = if (nbErrors > 0) context.resources.getQuantityString(
            R.plurals.notif_archive_fail_details,
            nbErrors,
            nbErrors
        )
        else context.resources.getQuantityString(
            R.plurals.notif_archive_complete_details,
            books,
            books
        )

        return NotificationCompat.Builder(context, ArchiveNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(title))
            .setContentText(contentTxt)
            .setContentIntent(getIntent(context))
            .build()
    }

    private fun getIntent(context: Context): PendingIntent {
        val intent = Intent(context, ArchiveNotificationSuccessReceiver::class.java)
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_CANCEL_CURRENT
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
