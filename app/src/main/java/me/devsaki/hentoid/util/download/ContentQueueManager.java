package me.devsaki.hentoid.util.download;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.workers.ContentDownloadWorker;

import static me.devsaki.hentoid.core.Consts.WORK_CLOSEABLE;

/**
 * Created by Robb_w on 2018/04
 * Manager class for Content (book) download queue
 * <p>
 * By default at app launch :
 * - Queue is inactive (i.e. does not do anything)
 * - Queue is unpaused (i.e. will immediately start to download when activated)
 * <p>
 * At first download command :
 * - Queue becomes active
 * - Queue "stays" unpaused => Download starts
 */
public class ContentQueueManager {
    private static ContentQueueManager mInstance;   // Instance of the singleton

    private boolean isQueuePaused;                  // True if queue paused; false if not
    private boolean isQueueActive;                  // True if queue active; false if not
    private int downloadCount = 0;                  // Used to store the number of downloads completed during current session
    // in order to display notifications correctly ("download completed" vs. "N downloads completed")

    private ContentQueueManager() {
        isQueuePaused = false;
        isQueueActive = false;
    }

    public static synchronized ContentQueueManager getInstance() {
        if (mInstance == null) {
            mInstance = new ContentQueueManager();
        }
        return mInstance;
    }


    // QUEUE ACTIVITY CONTROL
    public void pauseQueue() {
        isQueuePaused = true;
    }

    public void unpauseQueue() {
        isQueuePaused = false;
    }

    public boolean isQueuePaused() {
        return isQueuePaused;
    }

    public boolean isQueueActive() {
        return isQueueActive;
    }

    public void setInactive() {
        isQueueActive = false;
    }

    public void resumeQueue(Context context) {
        if (!isQueueActive) {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniqueWork(
                    Integer.toString(R.id.download_service),
                    ExistingWorkPolicy.KEEP,
                    new OneTimeWorkRequest.Builder(ContentDownloadWorker.class).addTag(WORK_CLOSEABLE).build()
            );
            isQueueActive = true;
        }
    }


    // DOWNLOAD COUNTER MANAGEMENT
    public int getDownloadCount() {
        return downloadCount;
    }

    public void resetDownloadCount() {
        this.downloadCount = 0;
    }

    /**
     * Signals a new completed download
     */
    public void downloadComplete() {
        downloadCount++;
    }
}
