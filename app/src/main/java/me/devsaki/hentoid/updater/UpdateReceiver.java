package me.devsaki.hentoid.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 8/21/15.
 * Broadcast Receiver for updater.
 */
public class UpdateReceiver extends BroadcastReceiver {
    private static final String TAG = LogHelper.makeLogTag(UpdateReceiver.class);

    private UpdateCheck instance;

    public UpdateReceiver() {
    }

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
                LogHelper.d(TAG, "Cancel Update Download");
            }
            if (action.equals(UpdateCheck.ACTION_DOWNLOAD_UPDATE)) {
                instance.downloadUpdate();
                instance.downloadingUpdateNotification();
                LogHelper.d(TAG, "Download Update");
            }
            if (action.equals(UpdateCheck.ACTION_INSTALL_UPDATE)) {
                instance.cancelNotificationAndUpdateRunnable();
                instance.installUpdate();
                LogHelper.d(TAG, "Install Update");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
