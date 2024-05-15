package me.devsaki.hentoid.receiver;

import static me.devsaki.hentoid.util.file.FileHelperKt.getDownloadsFolder;
import static me.devsaki.hentoid.util.file.FileHelperKt.openFile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver for when an archival notification is clicked
 */
public class ArchiveNotificationSuccessReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        openFile(context, getDownloadsFolder());
    }
}
