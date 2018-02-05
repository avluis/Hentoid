package me.devsaki.hentoid.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import timber.log.Timber;

/**
 * Created by avluis on 8/21/15.
 * Broadcast Receiver for updater.
 */
public class UpdateReceiver extends BroadcastReceiver {

    private UpdateCheck instance;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (instance == null) {
            instance = UpdateCheck.getInstance();
        }
        try {
            String action = intent.getAction();
            if (action.equals(UpdateCheck.ACTION_DOWNLOAD_CANCELLED) ||
                    (action.equals(UpdateCheck.ACTION_NOTIFICATION_REMOVED))) {
                instance.cancelDownload();
                Timber.d("Cancel Update Download");
            }
            if (action.equals(UpdateCheck.ACTION_DOWNLOAD_UPDATE)) {
                instance.downloadUpdate();
                instance.downloadingUpdateNotification();
                Timber.d("Download Update");
            }
            if (action.equals(UpdateCheck.ACTION_INSTALL_UPDATE)) {
                instance.cancelNotificationAndUpdateRunnable();
                instance.installUpdate();
                Timber.d("Install Update");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
