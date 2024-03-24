package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import timber.log.Timber

/**
 * Broadcast receiver for the cancel button on download notifications
 */
class DownloadNotificationCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.IO) {
                val dao: CollectionDAO = ObjectBoxDAO()
                try {
                    val queue = dao.selectQueue()
                    if (queue.isNotEmpty()) {
                        val content = queue[0].content.target
                        ContentHelper.removeQueuedContent(context, dao, content, true)
                    }
                } catch (e: ContentNotProcessedException) {
                    Timber.w(e)
                } finally {
                    dao.cleanup()
                }
            }
        }
    }
}