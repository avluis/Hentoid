package me.devsaki.hentoid.notification.import_

import android.content.Context
import androidx.core.app.NotificationCompat

import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.Notification

class ImportCompleteNotification(private val booksOK: Int, private val booksKO: Int) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ImportNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_hentoid)
            .setContentTitle("Import complete")
            .setContentText("$booksOK imported successfuly; $booksKO failed")
            .build()
}
