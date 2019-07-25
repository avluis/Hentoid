package me.devsaki.hentoid.notification.import_;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.notification.Notification;

public class ImportCompleteNotification implements Notification {

    private final int booksOK;
    private final int booksKO;

    public ImportCompleteNotification(int booksOK, int booksKO)
    {
        this.booksOK = booksOK;
        this.booksKO = booksKO;
    }

    @NonNull
    @Override
    public android.app.Notification onCreateNotification(Context context) {
        return new NotificationCompat.Builder(context, ImportNotificationChannel.ID)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setContentTitle("Import complete")
                .setContentText(booksOK + " imported successfuly; " + booksKO +" failed")
                .build();
    }
}
