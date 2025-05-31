package me.devsaki.hentoid.notification.duplicates

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class DuplicateCompleteNotification(private val nbDuplicates: Int) : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.resources.getText(R.string.duplicate_notif_complete_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.duplicate_notif_complete_desc,
                    nbDuplicates,
                    nbDuplicates
                )
            )
            .build()
}
