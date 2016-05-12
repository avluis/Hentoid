package me.devsaki.hentoid.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.devsaki.hentoid.HentoidApp;

/**
 * Created by avluis on 04/08/2016.
 * Broadcast receiver for download related notifications.
 */
public class NotificationHelper extends BroadcastReceiver {
    public static final String NOTIFICATION_DELETED =
            "me.devsaki.hentoid.services.NOTIFICATION_DELETED";

    private HentoidApp instance;

    public NotificationHelper() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (instance == null) {
            instance = HentoidApp.getInstance();
        }

        try {
            String action = intent.getAction();
            if (action.equals(NOTIFICATION_DELETED)) {
                // Reset download count
                HentoidApp.setDownloadCount(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}