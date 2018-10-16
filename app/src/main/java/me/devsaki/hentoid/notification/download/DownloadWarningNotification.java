package me.devsaki.hentoid.notification.download;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class DownloadWarningNotification implements Notification {

    private final String title;

    private final String absolutePath;

    public DownloadWarningNotification(String title, String absolutePath) {
        this.title = title;
        this.absolutePath = absolutePath;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid_warning)
                .setContentTitle("Warning : download failed")
                .setStyle(getBigStyle())
                .setLocalOnly(true)
                .build();
    }

    private NotificationCompat.BigTextStyle getBigStyle() {
        String template = "Cannot download %s : unable to create folder %s. Please check your Hentoid folder and retry downloading using the (!) button.";
        String message = String.format(template, title, absolutePath);
        return new NotificationCompat.BigTextStyle().bigText(message);
    }
}
