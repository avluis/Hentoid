package me.devsaki.hentoid.notification.action

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import androidx.core.app.NotificationCompat
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.notification.Notification

class UserActionNotification(val site: Site, private val oldCookie: String) : Notification {

    override fun onCreateNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, UserActionNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_hentoid_shape)
            .setContentTitle(context.resources.getText(R.string.notification_user_action_needed))
            .setContentText(context.resources.getText(R.string.notification_action_dl_revive))
            .setContentIntent(getDefaultIntent(context))
            .setLocalOnly(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    private fun getDefaultIntent(context: Context): PendingIntent {
        val resultIntent = Intent(context, QueueActivity::class.java)
        resultIntent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK// or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val bundleBuilder = QueueActivityBundle()
        bundleBuilder.reviveDownloadForSiteCode = site.code
        bundleBuilder.reviveOldCookie = oldCookie
        resultIntent.putExtras(bundleBuilder.bundle)

        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(
            context,
            0,
            resultIntent,
            flags
        )
    }
}
