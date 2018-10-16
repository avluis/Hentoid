package me.devsaki.hentoid.notification.download;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.receiver.DownloadNotificationDeleteReceiver;
import me.devsaki.hentoid.util.notification.Notification;

public class DownloadSuccessNotification implements Notification {

    private final int completeCount;

    public DownloadSuccessNotification(int completeCount) {
        this.completeCount = completeCount;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle(getTitle(context))
                .setContentIntent(getDefaultIntent(context))
                .setDeleteIntent(getDeleteIntent(context))
                .setLocalOnly(true)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
    }

    private String getTitle(Context context) {
        return context.getResources()
                .getQuantityString(R.plurals.download_completed, completeCount, completeCount);
    }

    private PendingIntent getDefaultIntent(Context context) {
        Intent resultIntent = new Intent(context, DownloadsActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getDeleteIntent(Context context) {
        Intent intent = new Intent(context, DownloadNotificationDeleteReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
