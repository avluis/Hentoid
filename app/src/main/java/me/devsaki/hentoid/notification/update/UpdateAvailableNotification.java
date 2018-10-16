package me.devsaki.hentoid.notification.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.services.UpdateDownloadService;
import me.devsaki.hentoid.util.PendingIntentCompat;
import me.devsaki.hentoid.util.notification.Notification;

public class UpdateAvailableNotification implements Notification {

    private final String updateUrl;

    public UpdateAvailableNotification(String updateUrl) {
        this.updateUrl = updateUrl;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        Intent intent = UpdateDownloadService.makeIntent(context, updateUrl);

        PendingIntent pendingIntent = PendingIntentCompat.getForegroundService(context, intent);

        return new NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(new long[]{1, 1, 1})
                .setContentTitle("An update is available!")
                .setContentText("Tap to download")
                .setContentIntent(pendingIntent)
                .build();
    }
}
