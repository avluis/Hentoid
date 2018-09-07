package me.devsaki.hentoid.notification.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class UpdateInstallNotification implements Notification {

    private final Uri apkUri;

    public UpdateInstallNotification(Uri apkUri) {
        this.apkUri = apkUri;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, apkUri);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        return new NotificationCompat.Builder(context, UpdateNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(new long[]{1, 1, 1})
                .setContentTitle("Update ready")
                .setContentText("Tap to install")
                .setContentIntent(pendingIntent)
                .build();
    }
}
