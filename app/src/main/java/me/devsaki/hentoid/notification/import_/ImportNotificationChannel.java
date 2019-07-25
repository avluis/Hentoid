package me.devsaki.hentoid.notification.import_;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import java.util.Objects;

public class ImportNotificationChannel {

    private static final String ID_OLD = "import";
    static final String ID = "import2";

    public static void init(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "Library imports";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(ID, name, importance);
            channel.setSound(null, null);
            channel.setVibrationPattern(null);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            // Mandatory; it is not possible to change the sound of an existing channel after its initial creation
            notificationManager.deleteNotificationChannel(ID_OLD);

            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }
    }
}
