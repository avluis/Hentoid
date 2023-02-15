package me.devsaki.hentoid.util.notification;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;

import me.devsaki.hentoid.util.file.PermissionHelper;

public class NotificationManager {

    final Context context;

    final int notificationId;

    public NotificationManager(@NonNull Context context, @IdRes int notificationId) {
        this.context = context;
        this.notificationId = notificationId;
    }

    public void notify(@NonNull Notification notification) {
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        if (PermissionHelper.checkNotificationPermission(context))
            managerCompat.notify(notificationId, notification.onCreateNotification(context));
    }

    // To be used within workers for the last notification to be displayed
    // so that the termination of the worker doesn't clear that notification
    // (see https://stackoverflow.com/questions/60693832/workmanager-keep-notification-after-work-is-done)
    public void notifyLast(@NonNull Notification notification) {
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        if (PermissionHelper.checkNotificationPermission(context))
            managerCompat.notify(notificationId + 1, notification.onCreateNotification(context));
    }

    public ForegroundInfo buildForegroundInfo(@NonNull Notification notification) {
        return new ForegroundInfo(notificationId, notification.onCreateNotification(context));
    }

    public void cancel() {
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.cancel(notificationId);
    }
}
