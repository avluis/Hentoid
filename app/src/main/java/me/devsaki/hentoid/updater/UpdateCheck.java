package me.devsaki.hentoid.updater;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.text.format.Formatter;
import android.widget.RemoteViews;

import com.thin.downloadmanager.DownloadManager;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.ConstsUpdater;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Created by avluis on 8/21/15.
 * Takes care of notifying and download of app updates.
 */
public class UpdateCheck {
    static final String ACTION_DOWNLOAD_UPDATE =
            "me.devsaki.hentoid.updater.DOWNLOAD_UPDATE";
    static final String ACTION_NOTIFICATION_REMOVED =
            "me.devsaki.hentoid.updater.NOTIFICATION_REMOVED";
    static final String ACTION_DOWNLOAD_CANCELLED =
            "me.devsaki.hentoid.updater.DOWNLOAD_CANCELLED";
    static final String ACTION_INSTALL_UPDATE =
            "me.devsaki.hentoid.updater.INSTALL_UPDATE";

    private static final String TAG = LogHelper.makeLogTag(UpdateCheck.class);

    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_UPDATED_URL = "updateURL";
    @SuppressLint("StaticFieldLeak")
    private static UpdateCheck instance;
    private final int NOTIFICATION_ID = ConstsUpdater.UPDATE_NOTIFICATION_ID;
    private final UpdateNotificationRunnable updateNotificationRunnable =
            new UpdateNotificationRunnable();
    private NotificationCompat.Builder builder;
    private NotificationManager notificationManager;
    private RemoteViews notificationView;
    private int downloadID = -1;
    private DownloadManager downloadManager;
    private String updateDownloadPath;
    private Context cxt;
    private UpdateCheckCallback updateCheckResult;
    private Handler mHandler;
    private String downloadURL;
    private int progressBar;
    private long total;
    private long done;
    private boolean showToast;
    private int retryCount = 0;

    public static UpdateCheck getInstance() {
        if (instance == null) {
            instance = new UpdateCheck();
        }

        return instance;
    }

    public void checkForUpdate(@NonNull Context context, final boolean onlyWifi,
                               final boolean showToast, final UpdateCheckCallback callback) {
        this.cxt = context;
        this.updateCheckResult = callback;
        mHandler = new Handler(context.getMainLooper());

        if ((onlyWifi && NetworkStatus.isWifi(context)) ||
                (!onlyWifi && NetworkStatus.isOnline(context))) {
            checkNetworkConnectivity();
        } else {
            LogHelper.w(TAG, "Network is not connected!");
        }
        this.showToast = showToast;
    }

    private void checkNetworkConnectivity() {
        AsyncTask.execute(() -> {
            boolean connected = NetworkStatus.hasInternetAccess(HentoidApp.getAppContext());

            if (connected) {
                runAsyncTask(retryCount != 0);
            }
        });
    }

    private void runAsyncTask(boolean retry) {
        String updateURL;

        if (retry) {
            updateURL = ConstsUpdater.UPDATE_URL;
            retryCount++;
            LogHelper.d(TAG, "Retrying! Count: " + retryCount);
        } else {
            if (BuildConfig.DEBUG) {
                updateURL = ConstsUpdater.DEBUG_UPDATE_URL;
            } else {
                updateURL = ConstsUpdater.UPDATE_URL;
            }
        }

        LogHelper.d(TAG, "Update URL: " + updateURL);
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.HONEYCOMB)) {
            new UpdateCheckTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, updateURL);
        } else {
            new UpdateCheckTask().execute(updateURL);
        }
    }

    private void updateAvailableNotification(String updateURL) {
        downloadURL = updateURL;

        Intent installUpdate = new Intent(ACTION_DOWNLOAD_UPDATE);
        installUpdate.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent updateIntent = PendingIntent.getBroadcast(cxt, 0, installUpdate, 0);

        notificationView = new RemoteViews(cxt.getPackageName(),
                R.layout.notification_update_available);
        notificationManager = (NotificationManager)
                cxt.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat
                .Builder(cxt)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(new long[]{1, 1, 1})
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setTicker(cxt.getString(R.string.update_available))
                .setContent(notificationView);
        notificationView.setTextViewText(R.id.tv_2,
                cxt.getString(R.string.download_update));
        notificationView.setOnClickPendingIntent(R.id.rl_notify_root, updateIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());

        if (downloadManager != null) {
            try {
                mHandler.post(updateNotificationRunnable);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void downloadingUpdateNotification() {
        Intent stopIntent = new Intent(ACTION_DOWNLOAD_CANCELLED);
        stopIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent cancelIntent = PendingIntent.getBroadcast(cxt, 0, stopIntent, 0);

        Intent clearIntent = new Intent(ACTION_NOTIFICATION_REMOVED);
        clearIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent removeIntent = PendingIntent.getBroadcast(cxt, 0, clearIntent, 0);

        notificationView = new RemoteViews(cxt.getPackageName(),
                R.layout.notification_update);
        notificationManager = (NotificationManager)
                cxt.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat
                .Builder(cxt)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setTicker(cxt.getString(R.string.downloading_update))
                .setAutoCancel(false)
                .setOngoing(true)
                .setContent(notificationView)
                .setDeleteIntent(removeIntent);
        notificationView.setProgressBar(R.id.pb_notification, 100, 0, true);
        notificationView.setTextViewText(R.id.tv_1, cxt.getString(R.string.downloading_update));
        notificationView.setTextViewText(R.id.tv_2, "");
        notificationView.setOnClickPendingIntent(R.id.bt_cancel, cancelIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateDownloadedNotification() {
        Intent installUpdate = new Intent(ACTION_INSTALL_UPDATE);
        installUpdate.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent installIntent = PendingIntent.getBroadcast(cxt, 0, installUpdate, 0);

        Intent clearIntent = new Intent(ACTION_NOTIFICATION_REMOVED);
        clearIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent removeIntent = PendingIntent.getBroadcast(cxt, 0, clearIntent, 0);

        notificationView = new RemoteViews(cxt.getPackageName(),
                R.layout.notification_update_available);
        notificationManager = (NotificationManager)
                cxt.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat
                .Builder(cxt)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(new long[]{1, 1, 1})
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDeleteIntent(removeIntent)
                .setTicker(cxt.getString(R.string.install_update))
                .setContent(notificationView);
        notificationView.setTextViewText(R.id.tv_2, cxt.getString(R.string.install_update));
        notificationView.setOnClickPendingIntent(R.id.rl_notify_root, installIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    void installUpdate() {
        Intent intent;

        if (Helper.isAtLeastAPI(Build.VERSION_CODES.JELLY_BEAN)) {
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
        }

        intent.setDataAndType(Uri.parse("file://" + updateDownloadPath),
                "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        cxt.startActivity(intent);
    }

    void downloadUpdate() {
        if (downloadURL != null) {

            if (downloadID != -1) {
                cancelDownload();
            }

            Uri downloadUri = Uri.parse(downloadURL);
            Uri destinationUri = Uri.parse(updateDownloadPath = cxt.getExternalCacheDir() +
                    "/hentoid_update.apk");

            DownloadRequest downloadRequest = new DownloadRequest(downloadUri)
                    .setDestinationURI(destinationUri)
                    .setPriority(DownloadRequest.Priority.HIGH)
                    .setDeleteDestinationFileOnFailure(false)
                    .setStatusListener(new DownloadStatusListenerV1() {
                        private boolean posted;

                        @Override
                        public void onDownloadComplete(DownloadRequest request) {
                            cancelRunnable();
                            updateDownloadedNotification();
                        }

                        @Override
                        public void onDownloadFailed(DownloadRequest request, int errorCode,
                                                     String errorMessage) {
                            cancelNotificationAndUpdateRunnable();
                            LogHelper.d(TAG, "Error Code: " + errorCode + ". Error Message: " +
                                    errorMessage);
                            if (errorCode == DownloadManager.ERROR_UNHANDLED_HTTP_CODE &&
                                    "Unhandled HTTP response:404 message:Not Found"
                                            .equals(errorMessage)) {
                                try {
                                    notificationView.setProgressBar(R.id.pb_notification, 100, 0,
                                            true);
                                    notificationView.setTextViewText(R.id.tv_1,
                                            cxt.getString(R.string.error_network));
                                    notificationView.setTextViewText(R.id.tv_2,
                                            cxt.getString(R.string.error_file));
                                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
//                            } else if (errorCode != DownloadManager.ERROR_DOWNLOAD_CANCELLED) {
//                                try {
//                                    notificationView.setProgressBar(R.id.pb_notification, 100, 0,
//                                            true);
//                                    notificationView.setTextViewText(R.id.tv_1,
//                                            cxt.getString(R.string.error_network));
//                                    notificationView.setTextViewText(R.id.tv_2,
//                                            cxt.getString(R.string.error));
//                                    notificationManager.notify(NOTIFICATION_ID, builder.build());
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }
                            }
                            downloadManager = null;
                        }

                        @Override
                        public void onProgress(DownloadRequest request, long totalBytes,
                                               long downloadedBytes, int progress) {
                            progressBar = progress;
                            total = totalBytes;
                            done = downloadedBytes;

                            if (!posted) {
                                mHandler.post(updateNotificationRunnable);
                                posted = true;
                            }
                        }
                    });

            downloadManager = new ThinDownloadManager();
            downloadID = downloadManager.add(downloadRequest);
            LogHelper.d(TAG, "DownloadID: " + downloadID);
        } else {
            this.cancelNotification();
        }
    }

    void cancelNotificationAndUpdateRunnable() {
        cancelNotification();
        try {
            mHandler.removeCallbacks(updateNotificationRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cancelRunnable() {
        try {
            mHandler.removeCallbacks(updateNotificationRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void cancelDownload() {
        if (downloadManager != null) {
            try {
                downloadManager.cancel(downloadID);
                downloadManager.release();
            } catch (Exception e) {
                HentoidApp.getInstance().trackException(e);
                LogHelper.d(TAG, "Issue cancelling download: ", e);
            }
        }
        cancelNotificationAndUpdateRunnable();
    }

    public interface UpdateCheckCallback {
        void noUpdateAvailable();

        void onUpdateAvailable();
    }

    private class UpdateCheckTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                JSONObject jsonObject = new JsonHelper().jsonReader(params[0]);
                if (jsonObject != null) {
                    int updateVersionCode = jsonObject.getInt(KEY_VERSION_CODE);
                    if (Helper.getAppVersionCode(cxt) < updateVersionCode) {
                        if (updateCheckResult != null) {
                            updateCheckResult.onUpdateAvailable();
                        }
                        downloadURL = jsonObject.getString(KEY_UPDATED_URL);
                        updateAvailableNotification(downloadURL);
                    } else {
                        if (updateCheckResult != null) {
                            updateCheckResult.noUpdateAvailable();
                            if (showToast) {
                                mHandler.post(() -> Helper.toast(cxt,
                                        R.string.update_check_no_update));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (retryCount == 0) {
                    runAsyncTask(true);
                } else {
                    LogHelper.e(TAG, "IO ERROR: ", e);
                    HentoidApp.getInstance().trackException(e);

                    mHandler.post(() -> Helper.toast(cxt, R.string.error_dependency));
                }
            } catch (JSONException e) {
                HentoidApp.getInstance().trackException(e);
                LogHelper.e(TAG, "Error with JSON File: ", e);
                mHandler.post(() -> Helper.toast(cxt, R.string.error_dependency));
            } catch (PackageManager.NameNotFoundException e) {
                HentoidApp.getInstance().trackException(e);
                LogHelper.e(TAG, "Package Name NOT Found! ", e);
            } finally {
                retryCount = 0;
            }

            return null;
        }
    }

    private class UpdateNotificationRunnable implements Runnable {
        @Override
        public void run() {
            notificationView.setProgressBar(R.id.pb_notification, 100, progressBar, false);
            notificationView.setTextViewText(R.id.tv_2,
                    "(" + Formatter.formatShortFileSize(cxt, done) + "/"
                            + Formatter.formatShortFileSize(cxt, total) + ") "
                            + progressBar
                            + "%");
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            mHandler.postDelayed(this, 1000);
        }
    }
}
