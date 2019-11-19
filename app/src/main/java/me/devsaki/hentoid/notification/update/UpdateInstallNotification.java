package me.devsaki.hentoid.notification.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.notification.Notification;

public class UpdateInstallNotification implements Notification {

    private final static String APK_MIMETYPE = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk");
    private final Uri apkUri;

    public UpdateInstallNotification(Uri apkUri) {
        this.apkUri = apkUri;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            File f = new File(apkUri.getPath());
            Uri contentUri = FileProvider.getUriForFile(context, FileHelper.getFileProviderAuthority(), f);
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, contentUri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, APK_MIMETYPE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

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
