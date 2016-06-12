package me.devsaki.hentoid.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.greenrobot.eventbus.Subscribe;

import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by Shiro on 3/18/2016.
 * Responsible for handling download service notifications
 * Methods are intended to have default level accessors for use with DownloadService class only
 */
final class NotificationPresenter {
    private static final String TAG = LogHelper.makeLogTag(NotificationPresenter.class);

    private static final int NOTIFICATION_ID = 0;
    private final HentoidApp appInstance;
    private final Resources resources;
    private final NotificationManager notificationManager;

    private int downloadCount = 0;
    private Content currentContent;
    private NotificationCompat.Builder currentBuilder = null;

    NotificationPresenter() {
        appInstance = HentoidApp.getInstance();
        downloadCount = HentoidApp.getDownloadCount();
        resources = appInstance.getResources();
        notificationManager = (NotificationManager) appInstance
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        LogHelper.d(TAG, "Download Counter: " + downloadCount);
    }

    void downloadStarted(final Content content) {
        downloadCount++;
        currentContent = content;
        currentBuilder = new NotificationCompat.Builder(appInstance)
                .setContentText(currentContent.getTitle())
                .setSmallIcon(currentContent.getSite().getIco())
                .setColor(ContextCompat.getColor(appInstance.getApplicationContext(),
                        R.color.accent))
                .setLocalOnly(true);

        LogHelper.d(TAG, "Download Counter: " + downloadCount);

        updateNotification(0);
    }

    void downloadInterrupted(final Content content) {
        currentContent = content;
        updateNotification(0);
    }

    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        Double percent = event.percent;
        updateNotification(percent == -1 ? 0 : percent);
    }

    private void updateNotification(double percent) {
        currentBuilder.setContentIntent(getIntent());

        final StatusContent contentStatus = currentContent.getStatus();
        if (contentStatus == StatusContent.DOWNLOADING) {
            currentBuilder.setProgress(100, (int) percent, false)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentInfo(String.format(Locale.US, " %.2f", percent) + "%");
        } else {
            currentBuilder.setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentInfo("")
                    .setDefaults(Notification.DEFAULT_LIGHTS);
        }

        if (contentStatus == StatusContent.DOWNLOADED && downloadCount >= 1) {
            currentBuilder
                    .setSmallIcon(R.drawable.ic_stat_hentoid)
                    .setColor(ContextCompat.getColor(appInstance.getApplicationContext(),
                            R.color.accent))
                    .setContentText("")
                    .setDeleteIntent(getDeleteIntent())
                    .setContentTitle(resources.getQuantityString(R.plurals.download_completed,
                            downloadCount).replace("%d", String.valueOf(downloadCount))
                    );
            notificationManager.notify(NOTIFICATION_ID, currentBuilder.build());

            return;
        }
        switch (contentStatus) {
            case DOWNLOADING:
                currentBuilder.setContentTitle(resources.getString(R.string.downloading));
                break;
            case DOWNLOADED:
                currentBuilder.setContentTitle(resources.getQuantityString(
                        R.plurals.download_completed, downloadCount));
                // Tracking Event (Download Completed)
                appInstance.trackEvent("Download Service", "Download",
                        "Download Content: Success.");
                break;
            case PAUSED:
                currentBuilder.setContentTitle(resources.getString(R.string.download_paused));
                break;
            case CANCELED:
                currentBuilder.setContentTitle(resources.getString(R.string.download_cancelled));
                // Tracking Event (Download Cancelled)
                appInstance.trackEvent("Download Service", "Download",
                        "Download Content: Cancelled.");
                break;
            case ERROR:
                currentBuilder.setContentTitle(resources.getString(R.string.download_error));
                // Tracking Event (Download Error)
                appInstance.trackEvent("Download Service", "Download",
                        "Download Content: Error.");
                break;
            case UNHANDLED_ERROR:
                currentBuilder.setContentTitle(resources
                        .getString(R.string.unhandled_download_error));
                // Tracking Event (Download Unhandled Error)
                appInstance.trackEvent("Download Service", "Download",
                        "Download Content: Unhandled Error.");
                break;
        }
        notificationManager.notify(NOTIFICATION_ID, currentBuilder.build());
    }

    private PendingIntent getIntent() {
        Intent resultIntent = null;
        switch (currentContent.getStatus()) {
            case DOWNLOADED:
            case ERROR:
            case UNHANDLED_ERROR:
                resultIntent = new Intent(appInstance, DownloadsActivity.class);
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
                Bundle bundle = new Bundle();
                bundle.putInt(Consts.DOWNLOAD_COUNT, HentoidApp.getDownloadCount());
                resultIntent.putExtras(bundle);

                return PendingIntent.getActivity(appInstance, 0, resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            case DOWNLOADING:
            case PAUSED:
                resultIntent = new Intent(appInstance, QueueActivity.class);
                break;
            case CANCELED:
                resultIntent = new Intent(appInstance, currentContent.getWebActivityClass());
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Bundle cancelBundle = new Bundle();
                cancelBundle.putString(Consts.INTENT_URL, currentContent.getGalleryUrl());
                resultIntent.putExtras(cancelBundle);
                break;
        }

        return PendingIntent.getActivity(appInstance, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    private PendingIntent getDeleteIntent() {
        Intent clearIntent = new Intent(appInstance, NotificationHelper.class);
        clearIntent.setAction(NotificationHelper.NOTIFICATION_DELETED);
        clearIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getBroadcast(appInstance, 0, clearIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}