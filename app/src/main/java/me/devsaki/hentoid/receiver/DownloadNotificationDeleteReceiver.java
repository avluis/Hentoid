package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.devsaki.hentoid.util.download.ContentQueueManager;
import timber.log.Timber;

/**
 * Broadcast receiver for when a download notification is dismissed.
 */
public class DownloadNotificationDeleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ContentQueueManager.getInstance().resetDownloadCount();
        Timber.d("Download count reset to 0");
    }
}
