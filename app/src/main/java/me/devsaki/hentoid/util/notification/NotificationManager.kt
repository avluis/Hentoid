package me.devsaki.hentoid.util.notification

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import me.devsaki.hentoid.util.file.checkNotificationPermission

class NotificationManager(val context: Context, private val notificationId: Int) {

    fun notify(notification: BaseNotification) {
        val managerCompat = NotificationManagerCompat.from(context)
        if (context.checkNotificationPermission()) managerCompat.notify(
            notificationId,
            notification.onCreateNotification(context)
        )
    }

    // To be used within workers for the last notification to be displayed
    // so that the termination of the worker doesn't clear that notification
    // (see https://stackoverflow.com/questions/60693832/workmanager-keep-notification-after-work-is-done)
    fun notifyLast(notification: BaseNotification) {
        val managerCompat = NotificationManagerCompat.from(context)
        if (context.checkNotificationPermission()) managerCompat.notify(
            notificationId + 1,
            notification.onCreateNotification(context)
        )
    }

    fun buildForegroundInfo(notification: BaseNotification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification.onCreateNotification(context),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification.onCreateNotification(context))
        }
    }

    fun cancel() {
        val managerCompat = NotificationManagerCompat.from(context)
        managerCompat.cancel(notificationId)
    }
}