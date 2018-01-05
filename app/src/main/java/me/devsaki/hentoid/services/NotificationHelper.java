package me.devsaki.hentoid.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import timber.log.Timber;

/**
 * Created by avluis on 04/08/2016.
 * Broadcast receiver for download related notifications.
 */
public class NotificationHelper extends BroadcastReceiver {
    public static final String NOTIFICATION_DELETED =
            "me.devsaki.hentoid.services.NOTIFICATION_DELETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            if (action.equals(NOTIFICATION_DELETED)) {
                // Reset download count
                // HentoidApp.setDownloadCount(0);
                Timber.d("Notification removed.");
            }

        } catch (Exception e) {
            Timber.e(e, "Notification Exception");
        }
    }
}
