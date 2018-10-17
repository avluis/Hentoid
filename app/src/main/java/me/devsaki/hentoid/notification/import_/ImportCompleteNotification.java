package me.devsaki.hentoid.notification.import_;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class ImportCompleteNotification implements Notification {

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, ImportNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle("Import complete")
                .build();
    }
}
