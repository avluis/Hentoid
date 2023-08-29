package me.devsaki.hentoid.notification.transform

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class TransformCompleteNotification(private val elements: Int, private val isError: Boolean) :
    BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = R.string.transform_complete
        val content = context.resources.getQuantityString(
            R.plurals.transform_complete_details,
            elements,
            elements
        )

        return NotificationCompat.Builder(context, TransformNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(title))
            .setContentText(content)
            .build()
    }
}
