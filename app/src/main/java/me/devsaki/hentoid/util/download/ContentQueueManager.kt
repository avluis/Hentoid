package me.devsaki.hentoid.util.download

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.workers.ContentDownloadWorker
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

const val WORK_TAG = "download_scheduled"

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
            cancelSchedule(context)
            val myData: Data = workDataOf("MANUAL_START" to true)
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                R.id.download_service.toString(),
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(ContentDownloadWorker::class.java)
                    .setInputData(myData)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
    }

    fun cancelSchedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    fun schedule(context: Context, timeStart: Int) {
        val now = LocalTime.now()
        val scheduledTime = LocalTime.ofSecondOfDay(timeStart * 60L)
        val delay = if (now < scheduledTime) {
            now.until(scheduledTime, ChronoUnit.MINUTES)
        } else {
            24 * 60 - scheduledTime.until(now, ChronoUnit.MINUTES)
        }

        val workRequest = PeriodicWorkRequest.Builder(
            ContentDownloadWorker::class.java,
            24,
            TimeUnit.HOURS
        )
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        workManager.enqueueUniquePeriodicWork(
            R.id.download_service_scheduled.toString(),
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            workRequest
        )
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