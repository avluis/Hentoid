package me.devsaki.hentoid.notification.import_

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class ImportCompleteNotification(private val booksOK: Int, private val booksKO: Int) :
    BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ImportNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(R.string.import_complete))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.import_complete_details_success,
                    booksOK,
                    booksOK
                ) + "; " + context.resources.getQuantityString(
                    R.plurals.import_complete_details_failure,
                    booksKO,
                    booksKO
                )
            )
            .build()
}
