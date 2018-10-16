package me.devsaki.hentoid.util.notification;

import android.app.Service;
import androidx.annotation.NonNull;

public class ServiceNotificationManager extends NotificationManager {

    public ServiceNotificationManager(@NonNull Service service, int notificationId) {
        super(service, notificationId);
    }

    public void startForeground(@NonNull Notification notification) {
        Service service = (Service) context;
        service.startForeground(notificationId, notification.onCreateNotification(context));
    }
}
