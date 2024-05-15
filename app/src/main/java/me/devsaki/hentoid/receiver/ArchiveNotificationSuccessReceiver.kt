package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.devsaki.hentoid.util.file.getDownloadsFolder
import me.devsaki.hentoid.util.file.openFile

/**
 * Broadcast receiver for when an archival notification is clicked
 */
class ArchiveNotificationSuccessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        openFile(context, getDownloadsFolder())
    }
}