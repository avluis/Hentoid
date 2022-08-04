package me.devsaki.hentoid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.devsaki.hentoid.util.file.FileHelper;

/**
 * Broadcast receiver for when an archival notification is clicked
 */
public class ArchiveNotificationSuccessReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        FileHelper.openFile(context, FileHelper.getDownloadsFolder());
    }
}
