package me.devsaki.hentoid.notification.update;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class UpdateCheckNotification implements Notification {

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle("Checking for updates")
                .setContentText("Please wait")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build();
    }
}
