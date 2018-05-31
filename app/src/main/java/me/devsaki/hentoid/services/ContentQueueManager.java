package me.devsaki.hentoid.services;

/**
 * Created by Robb_w on 2018/04
 * Manager class for Content (book) download queue
 */
public class ContentQueueManager {
    private static ContentQueueManager mInstance;   // Instance of the singleton

    private boolean isQueuePaused;                  // True if queue paused; false if active
    private int downloadCount = 0;                  // Used to store the number of downloads completed during current session
                                                    // in order to display notifications correctly ("download completed" vs. "N downloads completed")

    private ContentQueueManager() {
        isQueuePaused = false;
    }

    public static synchronized ContentQueueManager getInstance() {
        if (mInstance == null) {
            mInstance = new ContentQueueManager();
        }
        return mInstance;
    }


    // PAUSE CONTROL
    public void pauseQueue() {
        isQueuePaused = true;
    }

    public void unpauseQueue() {
        isQueuePaused = false;
    }

    public boolean isQueuePaused() {
        return isQueuePaused;
    }


    // DOWNLOAD COUNTER MANAGEMENT
    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    /**
     * Signals a new completed download
     */
    public void downloadComplete() {
        downloadCount++;
    }
}
