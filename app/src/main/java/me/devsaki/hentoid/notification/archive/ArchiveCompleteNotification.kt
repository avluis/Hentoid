package me.devsaki.hentoid.notification.archive

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.receiver.ArchiveNotificationSuccessReceiver
import me.devsaki.hentoid.util.notification.BaseNotification

class ArchiveCompleteNotification(private val books: Int, private val nbErrors: Int) :
    BaseNotification() {

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
        return getPendingIntentForAction(context, ArchiveNotificationSuccessReceiver::class.java)
    }
}
