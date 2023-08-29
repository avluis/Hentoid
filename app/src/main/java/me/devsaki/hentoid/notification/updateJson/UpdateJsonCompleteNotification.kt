package me.devsaki.hentoid.notification.updateJson

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class UpdateJsonCompleteNotification :
    BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification {
        val title = R.string.notif_json_complete

        return NotificationCompat.Builder(context, UpdateJsonNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.getString(title))
            .build()
    }
}
