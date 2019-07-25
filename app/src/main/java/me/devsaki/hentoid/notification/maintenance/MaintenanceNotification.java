package me.devsaki.hentoid.notification.maintenance;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class MaintenanceNotification implements Notification {

    private final String title;

    public MaintenanceNotification(String title) {
        this.title = title;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, MaintenanceNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle(context.getString(R.string.maintenance))
                .setContentText(title)
                .setColor(ContextCompat.getColor(context, R.color.secondary))
                .setLocalOnly(true)
                .setOngoing(true)
                .build();
    }
}
