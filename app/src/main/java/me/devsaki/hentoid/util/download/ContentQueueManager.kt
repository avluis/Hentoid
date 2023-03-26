package me.devsaki.hentoid.util.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.*
import me.devsaki.hentoid.workers.ContentDownloadWorker

object ContentQueueManager {
    // True if queue paused; false if not
    var isQueuePaused = false
        private set

    // Used to store the number of downloads completed during current session
    var downloadCount = 0
        private set


    fun pauseQueue() {
        isQueuePaused = true
    }

    fun unpauseQueue() {
        isQueuePaused = false
    }

    fun isQueueActive(context: Context): Boolean {
        return ContentDownloadWorker.isRunning(context)
    }

    fun resumeQueue(context: Context) {
        if (!isQueueActive(context)) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                R.id.download_service.toString(),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(ContentDownloadWorker::class.java)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
    }

    // DOWNLOAD COUNTER MANAGEMENT
    fun resetDownloadCount() {
        downloadCount = 0
    }

    /**
     * Signals a new completed download
     */
    fun downloadComplete() {
        downloadCount++
    }
}