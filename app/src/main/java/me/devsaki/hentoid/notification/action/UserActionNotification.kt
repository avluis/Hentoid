package me.devsaki.hentoid.notification.action

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.notification.Notification

class UserActionNotification(val site: Site, val oldCookie: String) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, UserActionNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.resources.getText(R.string.notification_action_title))
            .setContentText(context.resources.getText(R.string.notification_action_dl_revive))
            .setContentIntent(getDefaultIntent(context))
            .setLocalOnly(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    private fun getDefaultIntent(context: Context): PendingIntent {
        val resultIntent = Intent(context, QueueActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK// or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val bundleBuilder = QueueActivityBundle.Builder()
        bundleBuilder.setReviveDownload(site)
        bundleBuilder.setReviveOldCookie(oldCookie)
        resultIntent.putExtras(bundleBuilder.bundle)

        return PendingIntent.getActivity(
            context,
            0,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
