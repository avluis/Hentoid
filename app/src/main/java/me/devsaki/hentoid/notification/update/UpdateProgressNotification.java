package me.devsaki.hentoid.notification.update;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class UpdateProgressNotification implements Notification {

    public static final int INDETERMINATE = -1;

    private final int progress;

    public UpdateProgressNotification(int progress) {
        this.progress = progress;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle("Downloading update")
                .setProgress(100, progress, progress == INDETERMINATE)
                .setOnlyAlertOnce(true)
                .build();
    }
}
