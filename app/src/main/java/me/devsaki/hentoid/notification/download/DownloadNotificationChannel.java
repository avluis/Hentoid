package me.devsaki.hentoid.notification.download;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Objects;

import me.devsaki.hentoid.R;

public class DownloadNotificationChannel {

    private DownloadNotificationChannel() {
        throw new IllegalStateException("Utility class");
    }

    private static final String ID_OLD = "download";
    static final String ID = "downloads";

    // IMPORTANT : ALWAYS INIT THE CHANNEL BEFORE FIRING NOTIFICATIONS !
    public static void init(@NonNull Context context) {
        String name = context.getString(R.string.downloader_title);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(ID, name, importance);
        channel.setSound(null, null);
        channel.setVibrationPattern(null);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

        // Mandatory; it is not possible to change the sound of an existing channel after its initial creation
        Objects.requireNonNull(notificationManager, "notificationManager must not be null");
        notificationManager.deleteNotificationChannel(ID_OLD);
        notificationManager.createNotificationChannel(channel);
    }
}
