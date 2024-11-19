package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.removeQueuedContent
import timber.log.Timber

/**
 * Broadcast receiver for the cancel button on download notifications
 */
class DownloadNotificationCancelReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            val queue = dao.selectQueue()
            if (queue.isNotEmpty()) {
                val content = queue[0].content.target
                GlobalScope.launch {
                    removeQueuedContent(context, dao, content, true)
                    dao.cleanup()
                }
            }
        } catch (e: ContentNotProcessedException) {
            Timber.w(e)
        }
    }
}