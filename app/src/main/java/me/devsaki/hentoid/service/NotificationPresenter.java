package me.devsaki.hentoid.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;

import java.util.Locale;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.StatusContent;

/**
 * Created by Shiro on 3/18/2016.
 * Responsible for handling download service notifications
 * Methods are intended to have default level accessors for use with DownloadService class only
 * TODO: Reset notification when a download is paused (when there are multiple downloads).
 */
final class NotificationPresenter {

    private final static int notificationId = 0;
    private final HentoidApplication appInstance;
    private final Resources resources;
    private final NotificationManager notificationManager;

    private int downloadCount = 0;
    private Content currentContent;
    private NotificationCompat.Builder currentBuilder = null;

    NotificationPresenter() {
        appInstance = HentoidApplication.getInstance();
        resources = appInstance.getResources();
        notificationManager = (NotificationManager) appInstance
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    void downloadStarted(final Content content) {
        downloadCount++;
        currentContent = content;
        currentBuilder = new NotificationCompat.Builder(appInstance)
                .setContentText(currentContent.getTitle())
                .setSmallIcon(currentContent.getSite().getIco())
                .setLocalOnly(true);

        updateNotification(0);
    }

    void downloadInterrupted(final Content content) {
        currentContent = content;
        updateNotification(0);
    }

    void updateNotification(double percent) {
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

        if (contentStatus == StatusContent.DOWNLOADED && downloadCount > 1) {
            currentBuilder
                    .setSmallIcon(R.drawable.ic_hentoid)
                    .setContentText("")
                    .setContentTitle(resources.getString(R.string.download_completed_multiple)
                            .replace("%d", String.valueOf(downloadCount))
                    );
            notificationManager.notify(notificationId, currentBuilder.build());
            return;
        }
        switch (contentStatus) {
            case DOWNLOADING:
                currentBuilder.setContentTitle(resources.getString(R.string.downloading));
                break;
            case DOWNLOADED:
                currentBuilder.setContentTitle(resources.getString(R.string.download_completed));
                // Tracking Event (Download Completed)
                appInstance.trackEvent("Download Service", "Download",
                        "Download Content: Success.");
                break;
            case PAUSED:
                currentBuilder.setContentTitle(resources.getString(R.string.download_paused));
                break;
            case SAVED:
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

        notificationManager.notify(notificationId, currentBuilder.build());
    }

    private PendingIntent getIntent() {
        Intent resultIntent = null;
        switch (currentContent.getStatus()) {
            case DOWNLOADED:
            case ERROR:
            case UNHANDLED_ERROR:
                resultIntent = new Intent(appInstance, DownloadsActivity.class);
                break;
            case DOWNLOADING:
            case PAUSED:
                resultIntent = new Intent(appInstance, QueueActivity.class);
                break;
            case SAVED:
                resultIntent = new Intent(appInstance, currentContent.getWebActivityClass());
                resultIntent.putExtra("url", currentContent.getUrl());
                break;
        }
        return PendingIntent.getActivity(appInstance,
                0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
    }
}
