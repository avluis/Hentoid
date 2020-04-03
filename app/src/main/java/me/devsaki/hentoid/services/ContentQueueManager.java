package me.devsaki.hentoid.services;

import android.content.Context;
import android.content.Intent;

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
    void pauseQueue() {
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

    void setInactive() { isQueueActive = false; }

    public void resumeQueue(Context context) {
        Intent intent = new Intent(Intent.ACTION_SYNC, null, context, ContentDownloadService.class);
        context.startService(intent);
        isQueueActive = true;
    }


    // DOWNLOAD COUNTER MANAGEMENT
    int getDownloadCount() {
        return downloadCount;
    }

    public void resetDownloadCount() {
        this.downloadCount = 0;
    }

    /**
     * Signals a new completed download
     */
    void downloadComplete() {
        downloadCount++;
    }
}
