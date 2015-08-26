package com.thin.downloadmanager;

public interface DownloadManager {

    /**
     * Status when the download is currently pending.
     */
    int STATUS_PENDING = 1;

    /**
     * Status when the download is currently pending.
     */
    int STATUS_STARTED = 1 << 1;

    /**
     * Status when the download network call is connecting to destination.
     */
    int STATUS_CONNECTING = 1 << 2;

    /**
     * Status when the download is currently running.
     */
    int STATUS_RUNNING = 1 << 3;

    /**
     * Status when the download has successfully completed.
     */
    int STATUS_SUCCESSFUL = 1 << 4;

    /**
     * Status when the download has failed.
     */
    int STATUS_FAILED = 1 << 5;

    /**
     * Status when the download has failed.
     */
    int STATUS_NOT_FOUND = 1 << 6;

    /**
     * Status when the download is attempted for retry due to connection timeouts.
     */
    int STATUS_RETRYING = 1 << 7;

    /**
     * Error code when writing download content to the destination file.
     */
    int ERROR_FILE_ERROR = 1001;

    /**
     * Error code when an HTTP code was received that download manager can't
     * handle.
     */
    int ERROR_UNHANDLED_HTTP_CODE = 1002;

    /**
     * Error code when an error receiving or processing data occurred at the
     * HTTP level.
     */
    int ERROR_HTTP_DATA_ERROR = 1004;

    /**
     * Error code when there were too many redirects.
     */
    int ERROR_TOO_MANY_REDIRECTS = 1005;

    /**
     * Error code when size of the file is unknown.
     */
    int ERROR_DOWNLOAD_SIZE_UNKNOWN = 1006;

    /**
     * Error code when passed URI is malformed.
     */
    int ERROR_MALFORMED_URI = 1007;

    /**
     * Error code when download is cancelled.
     */
    int ERROR_DOWNLOAD_CANCELLED = 1008;

    /**
     * Error code when there is connection timeout after maximum retries
     */
    int ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES = 1009;

    int add(DownloadRequest request);

    int cancel(int downloadId);

    void cancelAll();

    int query(int downloadId);

    void release();
}
