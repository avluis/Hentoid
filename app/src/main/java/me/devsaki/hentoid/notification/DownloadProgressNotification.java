package me.devsaki.hentoid.notification;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class DownloadProgressNotification implements Notification {

    public static final int INDETERMINATE = -1;

    private final int progress;

    public DownloadProgressNotification(int progress) {
        this.progress = progress;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle("Downloading update")
                .setProgress(100, progress, progress == INDETERMINATE)
                .build();
    }
}
