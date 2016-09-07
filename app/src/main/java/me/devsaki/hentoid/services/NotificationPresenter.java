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
    private final HentoidApp instance;
    private final Resources res;
    private final NotificationManager manager;

    private int count = 0;
    private Content content;
    private NotificationCompat.Builder builder = null;

    NotificationPresenter() {
        instance = HentoidApp.getInstance();
        res = instance.getResources();
        count = HentoidApp.getDownloadCount();
        manager = (NotificationManager) instance.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancelAll();

        LogHelper.d(TAG, "Download Counter: " + count);
    }

    void downloadStarted(final Content content) {
        count++;
        this.content = content;
        builder = new NotificationCompat.Builder(instance)
                .setContentText(this.content.getTitle())
                .setSmallIcon(this.content.getSite().getIco())
                .setColor(ContextCompat.getColor(instance.getApplicationContext(),
                        R.color.accent))
                .setLocalOnly(true);

        LogHelper.d(TAG, "Download Counter: " + count);

        updateNotification(0);
    }

    void downloadInterrupted(final Content content) {
        this.content = content;
        updateNotification(0);
    }

    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        Double percent = event.percent;
        updateNotification(percent == -1 ? 0 : percent);
    }

    private void updateNotification(double percent) {
        builder.setContentIntent(getIntent());

        final StatusContent contentStatus = content.getStatus();
        if (contentStatus == StatusContent.DOWNLOADING) {
            if (percent == 0) {
                builder.setProgress(0, 0, false)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setContentInfo("Processing...");
            } else {
                builder.setProgress(100, (int) percent, false)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setContentInfo(String.format(Locale.US, " %.2f", percent) + "%");
            }
        } else {
            builder.setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentInfo("")
                    .setDefaults(Notification.DEFAULT_LIGHTS);
        }

        if (contentStatus == StatusContent.DOWNLOADED && count >= 1) {
            builder.setSmallIcon(R.drawable.ic_stat_hentoid)
                    .setColor(ContextCompat.getColor(instance.getApplicationContext(),
                            R.color.accent))
                    .setContentText("")
                    .setDeleteIntent(getDeleteIntent())
                    .setContentTitle(res.getQuantityString(R.plurals.download_completed,
                            count).replace("%d", String.valueOf(count)));
            manager.notify(NOTIFICATION_ID, builder.build());

            return;
        }
        switch (contentStatus) {
            case DOWNLOADING:
                builder.setContentTitle(res.getString(R.string.downloading));
                break;
            case DOWNLOADED:
                builder.setContentTitle(res.getQuantityString(
                        R.plurals.download_completed, count));
                // Tracking Event (Download Completed)
                instance.trackEvent("Download Service", "Download",
                        "Download Content: Success.");
                break;
            case PAUSED:
                builder.setContentTitle(res.getString(R.string.download_paused));
                break;
            case CANCELED:
                builder.setContentTitle(res.getString(R.string.download_cancelled));
                // Tracking Event (Download Cancelled)
                instance.trackEvent("Download Service", "Download",
                        "Download Content: Cancelled.");
                break;
            case ERROR:
                builder.setContentTitle(res.getString(R.string.download_error));
                // Tracking Event (Download Error)
                instance.trackEvent("Download Service", "Download",
                        "Download Content: Error.");
                break;
            case UNHANDLED_ERROR:
                builder.setContentTitle(res
                        .getString(R.string.unhandled_download_error));
                // Tracking Event (Download Unhandled Error)
                instance.trackEvent("Download Service", "Download",
                        "Download Content: Unhandled Error.");
                break;
            default: // do nothing
                break;
        }
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private PendingIntent getIntent() {
        Intent resultIntent = null;
        switch (content.getStatus()) {
            case DOWNLOADED:
            case ERROR:
            case UNHANDLED_ERROR:
                resultIntent = new Intent(instance, DownloadsActivity.class);
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
                Bundle bundle = new Bundle();
                bundle.putInt(Consts.DOWNLOAD_COUNT, HentoidApp.getDownloadCount());
                resultIntent.putExtras(bundle);

                return PendingIntent.getActivity(instance, 0, resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            case DOWNLOADING:
            case PAUSED:
                resultIntent = new Intent(instance, QueueActivity.class);
                break;
            case CANCELED:
                resultIntent = new Intent(instance, content.getWebActivityClass());
                resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Bundle cancelBundle = new Bundle();
                cancelBundle.putString(Consts.INTENT_URL, content.getGalleryUrl());
                resultIntent.putExtras(cancelBundle);
                break;
            default: // do nothing
                break;
        }

        return PendingIntent.getActivity(instance, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    private PendingIntent getDeleteIntent() {
        Intent intent = new Intent(instance, NotificationHelper.class);
        intent.setAction(NotificationHelper.NOTIFICATION_DELETED);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getBroadcast(instance, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
