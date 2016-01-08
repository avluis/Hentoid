package me.devsaki.hentoid.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by avluis on 8/21/15.
 */
public class UpdateNotificationReceiver extends BroadcastReceiver {
    private UpdateCheck instance;

    public UpdateNotificationReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (instance == null) {
            instance = UpdateCheck.getInstance();
        }
        try {
            String action = intent.getAction();
            if (action.equals(UpdateCheck.ACTION_DOWNLOAD_CANCELLED)) {
                try {
                    instance.cancelDownload();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (action.equalsIgnoreCase(UpdateCheck.ACTION_NOTIFICATION_REMOVED)) {
                instance.cancelNotificationAndUpdateRunnable();
            } else if (action.equals(UpdateCheck.ACTION_DOWNLOAD_UPDATE)) {
                instance.cancelNotification();
                instance.downloadingUpdateNotification();
                instance.downloadUpdate();
            } else if (action.equals(UpdateCheck.ACTION_UPDATE_DOWNLOADED)) {
                instance.cancelNotification();
                instance.installUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}