package me.devsaki.hentoid.util.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import me.devsaki.hentoid.util.file.PermissionHelper

class NotificationManager(val context: Context, private val notificationId: Int) {

    fun notify(notification: BaseNotification) {
        val managerCompat = NotificationManagerCompat.from(context)
        if (PermissionHelper.checkNotificationPermission(context)) managerCompat.notify(
            notificationId,
            notification.onCreateNotification(context)
        )
    }

    // To be used within workers for the last notification to be displayed
    // so that the termination of the worker doesn't clear that notification
    // (see https://stackoverflow.com/questions/60693832/workmanager-keep-notification-after-work-is-done)
    fun notifyLast(notification: BaseNotification) {
        val managerCompat = NotificationManagerCompat.from(context)
        if (PermissionHelper.checkNotificationPermission(context)) managerCompat.notify(
            notificationId + 1,
            notification.onCreateNotification(context)
        )
    }

    fun buildForegroundInfo(notification: BaseNotification): ForegroundInfo {
        return ForegroundInfo(notificationId, notification.onCreateNotification(context))
    }

    fun cancel() {
        val managerCompat = NotificationManagerCompat.from(context)
        managerCompat.cancel(notificationId)
    }
}