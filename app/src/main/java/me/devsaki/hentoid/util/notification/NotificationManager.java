package me.devsaki.hentoid.util.notification;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

public class NotificationManager {

    final Context context;

    final int notificationId;

    public NotificationManager(@NonNull Context context, int notificationId) {
        this.context = context;
        this.notificationId = notificationId;
    }

    public void notify(@NonNull Notification notification) {
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.notify(notificationId, notification.onCreateNotification(context));
    }

    public void cancel() {
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.cancel(notificationId);
    }
}
