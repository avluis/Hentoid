package me.devsaki.hentoid.notification.download;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.util.notification.Notification;

public class DownloadProgressNotification implements Notification {

    private final String title;

    private final int progress;

    private final int max;

    public DownloadProgressNotification(String title, int progress, int max) {
        this.title = title;
        this.progress = progress;
        this.max = max;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, DownloadNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle(context.getString(R.string.downloading))
                .setContentText(title)
                .setContentInfo(getProgressString())
                .setProgress(max, progress, false)
                .setColor(ContextCompat.getColor(context, R.color.secondary))
                .setContentIntent(getDefaultIntent(context))
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private PendingIntent getDefaultIntent(Context context) {
        Intent resultIntent = new Intent(context, QueueActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private String getProgressString() {
        return String.format(Locale.US, " %.2f%%", progress * 100.0 / max);
    }
}
