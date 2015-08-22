package com.thin.downloadmanager;

public class ThinDownloadManager implements DownloadManager {

    /** Download request queue takes care of handling the request based on priority. */
    private DownloadRequestQueue mRequestQueue;

    /**
    * Default constructor
    */
    public ThinDownloadManager() {
        mRequestQueue = new DownloadRequestQueue();
        mRequestQueue.start();
    }

    /** Constructor taking MAX THREAD POOL SIZE  Allows maximum of 4 threads.
    * Any number higher than four or less than one wont be respected.
    **/
    public ThinDownloadManager(int threadPoolSize) {
        mRequestQueue = new DownloadRequestQueue(threadPoolSize);
        mRequestQueue.start();
    }

    /**
    * Add a new download.  The download will start automatically once the download manager is
    * ready to execute it and connectivity is available.
    *
    * @param request the parameters specifying this download
    * @return an ID for the download, unique across the application.  This ID is used to make future
    * calls related to this download.
    * @throws IllegalArgumentException
    */
    @Override
    public int add(DownloadRequest request) throws IllegalArgumentException {
        if(request == null) {
            throw new IllegalArgumentException("DownloadRequest cannot be null");
        }

        return mRequestQueue.add(request);
    }


    @Override
    public int cancel(int downloadId) {
        return mRequestQueue.cancel(downloadId);
    }


    @Override
    public void cancelAll() {
        mRequestQueue.cancelAll();
    }


    @Override
    public int query(int downloadId) {
        return mRequestQueue.query(downloadId);
    }

    @Override
    public void release() {
        if(mRequestQueue != null) {
            mRequestQueue.release();
            mRequestQueue = null;
        }
    }
}


