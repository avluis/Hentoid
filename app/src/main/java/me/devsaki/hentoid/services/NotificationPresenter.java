package me.devsaki.hentoid.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.Consts;
import timber.log.Timber;

/**
 * Created by Shiro on 3/18/2016.
 * Responsible for handling download service notifications
 * Methods are intended to have default level accessors for use with ContentDownloadService class only
 */
final class NotificationPresenter {

    // Unique notification ID for the Hentoid app
    private static final int NOTIFICATION_ID = 0;
    // Hentoid instance
    private final HentoidApp instance;
    // NotificationManager used to spawn and update phone notifications
    private final NotificationManager manager;
    // Notification builder
    private NotificationCompat.Builder builder = null;


    NotificationPresenter() {
        instance = HentoidApp.getInstance();
        manager = (NotificationManager) instance.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();
    }

    /**
     * Signal the starting of a new download
     *
     * @param content Book to display in the download notification
     */
    void downloadStarted(final Content content) {
        int icon = R.drawable.ic_stat_hentoid;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon = content.getSite().getIco();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            builder = new NotificationCompat.Builder(instance, NotificationChannel.DEFAULT_CHANNEL_ID)
                    .setContentText(content.getTitle())
                    .setSmallIcon(icon)
                    .setColor(ContextCompat.getColor(instance.getApplicationContext(), R.color.accent))
                    .setLocalOnly(true);
        } else {
            builder = new NotificationCompat.Builder(instance)
                    .setContentText(content.getTitle())
                    .setSmallIcon(icon)
                    .setColor(ContextCompat.getColor(instance.getApplicationContext(), R.color.accent))
                    .setLocalOnly(true);
        }
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Handled event
     */
    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            case DownloadEvent.EV_PROGRESS:
                buildProgressNotification((event.pagesKO + event.pagesOK) * 100.0 / event.pagesTotal);
                break;
            case DownloadEvent.EV_PAUSE:
                buildPauseNotification();
                break;
            case DownloadEvent.EV_CANCEL:
                buildCancelNotification(event.content);
                break;
            case DownloadEvent.EV_SKIP:
                buildSkipNotification();
                break;
            case DownloadEvent.EV_COMPLETE:
                buildCompleteNotification(0 == event.pagesKO);
                break;
//            case DownloadEvent.EV_UNPAUSE : <-- nothing; used to restart download queue activity that will produce a Progress event
        }
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Notify download progress
     *
     * @param percent % of download complete
     */
    private void buildProgressNotification(double percent) {
        Timber.d("Event notified : progress / %s percent", String.valueOf(percent));

        builder.setContentIntent(getDefaultIntent());
        if (0 == percent) {
            builder.setProgress(0, 0, false)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentInfo("Processing...")
                    .setContentTitle(instance.getString(R.string.downloading));
        } else {
            builder.setProgress(100, (int) percent, false)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentInfo(String.format(Locale.US, " %.2f", percent) + "%")
                    .setContentTitle(instance.getString(R.string.downloading));
        }
    }

    /**
     * Notify a complete download
     *
     * @param isSuccess True if completed download is successful; false if there is at least 1 page whose download has failed
     */
    private void buildCompleteNotification(boolean isSuccess) {
        Timber.d("Event notified : complete with status %s", isSuccess);

        builder.setContentIntent(getDefaultIntent());

        builder.setSmallIcon(R.drawable.ic_stat_hentoid)
                .setColor(ContextCompat.getColor(instance.getApplicationContext(), R.color.accent))
                .setContentText("")
                .setDeleteIntent(getDeleteIntent());

        if (isSuccess) {
            int downloadCount = ContentQueueManager.getInstance().getDownloadCount();
            builder.setContentTitle(instance.getResources().getQuantityString(R.plurals.download_completed,
                    downloadCount).replace("%d", String.valueOf(downloadCount)));

            // Tracking Event (Download Success)
            HentoidApp.trackDownloadEvent("Success");
        } else {
            builder.setContentTitle(instance.getString(R.string.download_error));
            // Tracking Event (Download Error)
            HentoidApp.trackDownloadEvent("Error");
        }
    }

    /**
     * Notify paused download
     */
    private void buildPauseNotification() {
        Timber.d("Event notified : paused");

        builder.setContentIntent(getPausedIntent());
        builder.setContentTitle(instance.getString(R.string.download_paused));
    }

    /**
     * Notify canceled download
     *
     * @param content Canceled book
     */
    private void buildCancelNotification(Content content) {
        Timber.d("Event notified : cancelled");

        builder.setContentIntent(getCanceledIntent(content));
        builder.setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentInfo("")
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentTitle(instance.getString(R.string.download_cancelled));

        // Tracking Event (Download Canceled)
        HentoidApp.trackDownloadEvent("Cancelled");
    }

    /**
     * Notify skipped download
     */
    private void buildSkipNotification() {
        Timber.d("Event notified : skipped");

        builder.setContentIntent(getPausedIntent());
        builder.setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentInfo("")
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentTitle(instance.getString(R.string.download_cancelled));

        // Tracking Event (Download Skipped)
        HentoidApp.trackDownloadEvent("Skipped");
    }

    /**
     * Creates an intent pointing to the library screen (DownloadsActivity)
     *
     * @return Intent pointing to the library screen (DownloadsActivity)
     */
    private PendingIntent getDefaultIntent() {
        Intent resultIntent = new Intent(instance, DownloadsActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Bundle bundle = new Bundle();
        bundle.putInt(Consts.DOWNLOAD_COUNT, ContentQueueManager.getInstance().getDownloadCount());
        resultIntent.putExtras(bundle);

        return PendingIntent.getActivity(instance, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Creates an intent pointing to the downloads queue
     *
     * @return Intent pointing to the downloads queue
     */
    private PendingIntent getPausedIntent() {
        Intent resultIntent = new Intent(instance, QueueActivity.class);
        return PendingIntent.getActivity(instance, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    /**
     * Creates an intent pointing to the book web page
     *
     * @return Intent pointing to the book web page
     */
    private PendingIntent getCanceledIntent(Content content) {
        Intent resultIntent = new Intent(instance, content.getWebActivityClass());
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Bundle cancelBundle = new Bundle();
        cancelBundle.putString(Consts.INTENT_URL, content.getGalleryUrl());
        resultIntent.putExtras(cancelBundle);

        return PendingIntent.getActivity(instance, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
    }

    private PendingIntent getDeleteIntent() {
        Intent intent = new Intent(instance, NotificationHelper.class);
        intent.setAction(NotificationHelper.NOTIFICATION_DELETED);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getBroadcast(instance, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
