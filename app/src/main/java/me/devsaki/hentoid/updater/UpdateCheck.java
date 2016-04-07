package me.devsaki.hentoid.updater;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.text.format.Formatter;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.thin.downloadmanager.DownloadManager;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Created by avluis on 8/21/15.
 * Takes care of notifying and download of app updates.
 */
public class UpdateCheck implements IUpdateCheck {
    public static final String ACTION_DOWNLOAD_CANCELLED =
            "me.devsaki.hentoid.updater.DOWNLOAD_CANCELLED";
    public static final String ACTION_NOTIFICATION_REMOVED =
            "me.devsaki.hentoid.updater.NOTIFICATION_REMOVED";
    public static final String ACTION_DOWNLOAD_UPDATE =
            "me.devsaki.hentoid.updater.INSTALL_UPDATE";
    public static final String ACTION_UPDATE_DOWNLOADED =
            "me.devsaki.hentoid.updater.UPDATE_DOWNLOADED";

    private static final String TAG = LogHelper.makeLogTag(UpdateCheck.class);

    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_UPDATED_URL = "updateURL";
    private static volatile UpdateCheck instance;
    private final int NOTIFICATION_ID = 1;
    private final UpdateNotificationRunnable updateNotificationRunnable =
            new UpdateNotificationRunnable();
    private NotificationCompat.Builder builder;
    private NotificationManager notificationManager;
    private RemoteViews notificationView;
    private int downloadID = -1;
    private DownloadManager downloadManager;
    private String updateDownloadPath;
    private Context context;
    private UpdateCheckCallback updateCheckResult;
    private Handler mHandler;
    private String downloadURL;
    private int progressBar;
    private long total;
    private long done;
    private boolean showToast;
    private boolean connected;

    private UpdateCheck() {
    }

    public static UpdateCheck getInstance() {
        if (instance == null) {
            instance = new UpdateCheck();
        }

        return instance;
    }

    @Override
    public void checkForUpdate(Context context,
                               final boolean onlyWifi,
                               final boolean showToast,
                               final UpdateCheckCallback updateCheckResult) {
        if (context == null) {
            throw new NullPointerException("context or UpdateURL is null");
        }

        this.context = context;
        this.updateCheckResult = updateCheckResult;
        mHandler = new Handler(context.getMainLooper());

        if ((onlyWifi && NetworkStatus.isWifi(context)) ||
                (!onlyWifi && NetworkStatus.isOnline(context))) {
            connected = true;
        } else {
            LogHelper.w(TAG, "Network is not connected!");
        }
        if (connected) {
            runAsyncTask();
        }
        this.showToast = showToast;
    }

    private void runAsyncTask() {
        String updateURL;
        if (BuildConfig.DEBUG) {
            updateURL = Constants.DEBUG_UPDATE_URL;
        } else {
            updateURL = Constants.UPDATE_URL;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            new UpdateCheckTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, updateURL);
        } else {
            new UpdateCheckTask().execute(updateURL);
        }
    }

    @Override
    public int getAppVersionCode(Context context) throws PackageManager.NameNotFoundException {
        if (context != null) {
            return context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionCode;
        } else {
            throw new NullPointerException("context is null");
        }
    }

    private void updateAvailableNotification(String updateURL) {
        if (downloadManager != null) {
            downloadingUpdateNotification();
            try {
                mHandler.removeCallbacks(updateNotificationRunnable);
                mHandler.post(updateNotificationRunnable);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        downloadURL = updateURL;

        Intent installUpdate = new Intent(ACTION_DOWNLOAD_UPDATE);
        installUpdate.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent updateIntent = PendingIntent.getBroadcast(context, 0, installUpdate, 0);

        notificationView = new RemoteViews(context.getPackageName(),
                R.layout.notification_update_available);
        notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat
                .Builder(context)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(new long[]{1, 1, 1})
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setTicker(context.getString(R.string.update_available))
                .setContent(notificationView);
        notificationView.setTextViewText(R.id.tv_2,
                context.getString(R.string.download_update));
        notificationView.setOnClickPendingIntent(R.id.rl_notify_root, updateIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void downloadingUpdateNotification() {
        Intent stopIntent = new Intent(ACTION_DOWNLOAD_CANCELLED);
        stopIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent cancelIntent = PendingIntent.getBroadcast(context, 0, stopIntent, 0);

        Intent clearIntent = new Intent(ACTION_NOTIFICATION_REMOVED);
        clearIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent removeIntent = PendingIntent.getBroadcast(context, 0, clearIntent, 0);

        notificationView = new RemoteViews(context.getPackageName(),
                R.layout.notification_update);
        notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat
                .Builder(context)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setTicker(context.getString(R.string.downloading_update))
                .setContent(notificationView)
                .setDeleteIntent(removeIntent);
        notificationView.setProgressBar(R.id.pb_notification, 100, 0, true);
        notificationView.setTextViewText(R.id.tv_1, context.getString(R.string.downloading_update));
        notificationView.setTextViewText(R.id.tv_2, "");
        notificationView.setOnClickPendingIntent(R.id.bt_cancel, cancelIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateDownloadedNotification() {
        Intent installUpdate = new Intent(ACTION_UPDATE_DOWNLOADED);
        installUpdate.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent installIntent = PendingIntent.getBroadcast(context, 0, installUpdate, 0);

        notificationView = new RemoteViews(context.getPackageName(),
                R.layout.notification_update_available);
        notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat
                .Builder(context)
                .setSmallIcon(R.drawable.ic_stat_hentoid)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(new long[]{1, 1, 1})
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setTicker(context.getString(R.string.install_update))
                .setContent(notificationView);
        notificationView.setTextViewText(R.id.tv_2, context.getString(R.string.install_update));
        notificationView.setOnClickPendingIntent(R.id.rl_notify_root, installIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public void installUpdate() {
        Intent intent;

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            // noinspection deprecation
            intent.putExtra(Intent.EXTRA_ALLOW_REPLACE, true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
        }

        intent.setDataAndType(Uri.parse("file://" + updateDownloadPath),
                "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public void downloadUpdate() {
        if (downloadURL != null) {

            if (downloadID != -1) {
                cancelDownload();
            }

            Uri downloadUri = Uri.parse(downloadURL);
            Uri destinationUri = Uri.parse(updateDownloadPath =
                    context.getExternalCacheDir() + "/hentoid_update.apk");

            DownloadRequest downloadRequest = new DownloadRequest(downloadUri)
                    .setDestinationURI(destinationUri)
                    .setPriority(DownloadRequest.Priority.HIGH)
                    .setStatusListener(new DownloadStatusListenerV1() {
                        private boolean posted;

                        @Override
                        public void onDownloadComplete(DownloadRequest request) {
                            cancelNotificationAndUpdateRunnable();
                            updateDownloadedNotification();
                        }

                        @Override
                        public void onDownloadFailed(DownloadRequest request, int errorCode,
                                                     String errorMessage) {
                            cancelNotificationAndUpdateRunnable();
                            if (errorCode != DownloadManager.ERROR_DOWNLOAD_CANCELLED) {
                                try {
                                    notificationView.setProgressBar(R.id.pb_notification, 100, 0,
                                            true);
                                    notificationView.setTextViewText(R.id.tv_1,
                                            context.getString(R.string.error_network));
                                    notificationView.setTextViewText(R.id.tv_2,
                                            context.getString(R.string.error));
                                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
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
                                posted = true;
                                mHandler.post(updateNotificationRunnable);
                            }
                        }
                    });

            downloadManager = new ThinDownloadManager();
            downloadID = downloadManager.add(downloadRequest);
            LogHelper.d(TAG, "DownloadID: " + downloadID);
        } else {
            AndroidHelper.sToast(context, R.string.update_failed, Toast.LENGTH_LONG);
            instance.cancelNotification();
        }
    }

    public void cancelNotificationAndUpdateRunnable() {
        try {
            mHandler.removeCallbacks(updateNotificationRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cancelNotification();
    }

    public void cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelDownload() {
        cancelNotificationAndUpdateRunnable();
        try {
            downloadManager.cancel(downloadID);
            downloadManager.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface UpdateCheckCallback {
        void noUpdateAvailable();

        void onUpdateAvailable();
    }

    public class UpdateCheckTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                JSONObject jsonObject = downloadURL(params[0]);
                if (jsonObject != null) {
                    int updateVersionCode = jsonObject.getInt(KEY_VERSION_CODE);
                    if (getAppVersionCode(context) < updateVersionCode) {
                        if (updateCheckResult != null) {
                            updateCheckResult.onUpdateAvailable();
                        }
                        downloadURL = jsonObject.getString(KEY_UPDATED_URL);
                        updateAvailableNotification(downloadURL);
                    } else {
                        if (updateCheckResult != null) {
                            updateCheckResult.noUpdateAvailable();
                            if (showToast) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        AndroidHelper.sToast(context,
                                                "Update Check: No new updates.",
                                                Toast.LENGTH_SHORT);
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (IOException | JSONException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            return null;
        }

        JSONObject downloadURL(String updateURL) throws IOException, JSONException {
            InputStream inputStream = null;
            try {
                disableConnectionReuse();
                URL url = new URL(updateURL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                connection.connect();
                int response = connection.getResponseCode();

                LogHelper.d(TAG, "Response: " + response);

                inputStream = connection.getInputStream();
                String contentString = readInputStream(inputStream);

                return new JSONObject(contentString);
            } catch (IOException io) {
                // TODO: Log to Analytics
                LogHelper.e(TAG, "JSON file not found: ", io);
            } catch (JSONException jex) {
                // TODO: Log to Analytics
                LogHelper.e(TAG, "JSON file not properly formatted: ", jex);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            return null;
        }

        private void disableConnectionReuse() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                System.setProperty("http.keepAlive", "false");
            }
        }

        private String readInputStream(InputStream inputStream) throws IOException {
            StringBuilder stringBuilder = new StringBuilder(inputStream.available());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream,
                    Charset.forName("UTF-8")));
            String line = reader.readLine();

            while (line != null) {
                stringBuilder.append(line);
                line = reader.readLine();
            }

            return stringBuilder.toString();
        }
    }

    private class UpdateNotificationRunnable implements Runnable {
        @Override
        public void run() {
            notificationView.setProgressBar(R.id.pb_notification, 100, progressBar, false);
            notificationView.setTextViewText(R.id.tv_2,
                    "(" + Formatter.formatShortFileSize(context, done) + "/"
                            + Formatter.formatShortFileSize(context, total) + ") "
                            + progressBar
                            + "%");
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            mHandler.postDelayed(this, 1000 * 2);
        }
    }
}