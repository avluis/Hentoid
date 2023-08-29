package me.devsaki.hentoid.notification.update

import android.content.Context
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.notification.BaseNotification

class UpdateCheckNotification : BaseNotification() {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
            .setDefaults(0)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVibrate(null)
            .setSound(null)
            .setContentTitle(context.resources.getText(R.string.checking_updates))
            .setContentText(context.resources.getText(R.string.please_wait))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
}
