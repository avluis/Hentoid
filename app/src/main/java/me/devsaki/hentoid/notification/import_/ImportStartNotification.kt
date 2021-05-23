package me.devsaki.hentoid.notification.import_

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class ImportStartNotification : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
            NotificationCompat.Builder(context, ImportNotificationChannel.ID)
                    .setSmallIcon(R.drawable.ic_hentoid_shape)
                    .setContentTitle("Importing library")
                    .setContentText("Importing library")
                    .build()
}
