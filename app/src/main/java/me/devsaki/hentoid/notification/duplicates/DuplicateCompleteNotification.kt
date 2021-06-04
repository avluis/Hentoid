package me.devsaki.hentoid.notification.duplicates

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class DuplicateCompleteNotification(private val nbDuplicates: Int) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
            NotificationCompat.Builder(context, DuplicateNotificationChannel.ID)
                    .setSmallIcon(R.drawable.ic_hentoid_shape)
                    .setContentTitle(context.resources.getText(R.string.duplicate_notif_complete_title))
                    .setContentText(context.resources.getString(R.string.duplicate_notif_complete_desc, nbDuplicates))
                    .build()
}
