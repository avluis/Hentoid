package me.devsaki.hentoid.notification.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import java.util.Objects;

public class UpdateNotificationChannel {

    private static final String ID_OLD = "update";
    static final String ID = "update2";

    public static void init(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "App updates";
            int importance = NotificationManager.IMPORTANCE_MIN;
            NotificationChannel channel = new NotificationChannel(ID, name, importance);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            // Mandatory; it is not possible to change the sound of an existing channel after its initial creation
            notificationManager.deleteNotificationChannel(ID_OLD);

            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }
    }
}
