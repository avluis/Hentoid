package me.devsaki.hentoid.util.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.workers.ContentDownloadWorker

object ContentQueueManager {
    // True if queue paused; false if not
    private var isQueuePaused = false

    // Used to store the number of downloads completed during current session
    private var downloadCount = 0


    fun pauseQueue() {
        isQueuePaused = true
    }

    fun unpauseQueue() {
        isQueuePaused = false
    }

    fun isQueuePaused(): Boolean {
        return isQueuePaused
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
                    .addTag(Consts.WORK_CLOSEABLE).build()
            )
        }
    }

    // DOWNLOAD COUNTER MANAGEMENT
    fun getDownloadCount(): Int {
        return downloadCount
    }

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