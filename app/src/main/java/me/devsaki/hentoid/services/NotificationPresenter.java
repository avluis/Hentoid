package me.devsaki.hentoid.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
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
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by Shiro on 3/18/2016.
 * Responsible for handling download service notifications
 * Methods are intended to have default level accessors for use with DownloadService class only
 */
final class NotificationPresenter {

    private static final int NOTIFICATION_ID = 0;
    private final HentoidApp instance;
    private final Resources res;
    private final NotificationManager manager;

    private int count;
    private NotificationCompat.Builder builder = null;

    NotificationPresenter() {
        instance = HentoidApp.getInstance();
        res = instance.getResources();
        count = HentoidApp.getDownloadCount();
        manager = (NotificationManager) instance.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancelAll();

        Timber.d("Download Counter: %s", count);
    }

    void downloadStarted(final Content content) {
        count++;

        int icon = R.drawable.ic_stat_hentoid;
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.LOLLIPOP)) {
            icon = content.getSite().getIco();
        }

        if (Helper.isAtLeastAPI(Build.VERSION_CODES.N_MR1)) {
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

        Timber.d("Download Counter: %s", count);
    }

    // TODO remove
    void downloadInterrupted(final Content content) {
        //updateNotification(0, content);
    }

    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType)
        {
            case DownloadEvent.EV_PROGRESS:
                updateProgress((event.pagesKO + event.pagesOK) * 100.0 / event.pagesTotal);
                break;
            case DownloadEvent.EV_PAUSE :
                updatePause();
                break;
            case DownloadEvent.EV_CANCEL :
                updateCancel(event.content);
                break;
            case DownloadEvent.EV_SKIP :
                updateSkip();
                break;
            case DownloadEvent.EV_COMPLETE :
                updateComplete(event.content);
                break;
//            case DownloadEvent.EV_UNPAUSE : <-- nothing; used to restart download queue activity that will produce a Progress event
        }
    }

    private void updateProgress(double percent)
    {
        Timber.d("Event notified : progress / %s percent", String.valueOf(percent));

        builder.setContentIntent(getDefaultIntent());
        if (0 == percent) {
            builder.setProgress(0, 0, false)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentInfo("Processing...")
                    .setContentTitle(res.getString(R.string.downloading));
        } else {
            builder.setProgress(100, (int) percent, false)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentInfo(String.format(Locale.US, " %.2f", percent) + "%")
                    .setContentTitle(res.getString(R.string.downloading));
        }

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateComplete(Content content)
    {
        Timber.d("Event notified : complete with status %s", content.getStatus());

        builder.setContentIntent(getDefaultIntent());

        builder.setSmallIcon(R.drawable.ic_stat_hentoid)
                .setColor(ContextCompat.getColor(instance.getApplicationContext(), R.color.accent))
                .setContentText("")
                .setDeleteIntent(getDeleteIntent());

        if (content.getStatus().equals(StatusContent.DOWNLOADED)) {
            builder.setContentTitle(res.getQuantityString(R.plurals.download_completed,
                    count).replace("%d", String.valueOf(count)));

            // Tracking Event (Download Completed)
            instance.trackEvent(NotificationPresenter.class, "Download", "Download Content: Success.");
        } else {
            builder.setContentTitle(res.getString(R.string.download_error));
            // Tracking Event (Download Error)
            instance.trackEvent(NotificationPresenter.class, "Download", "Download Content: Error.");
        }
    }

    private void updatePause()
    {
        Timber.d("Event notified : paused");

        builder.setContentIntent(getPausedIntent());
        builder.setContentTitle(res.getString(R.string.download_paused));

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateCancel(Content content)
    {
        Timber.d("Event notified : cancelled");

        builder.setContentIntent(getCanceledIntent(content));
        builder.setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentInfo("")
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentTitle(res.getString(R.string.download_cancelled));

        // Tracking Event (Download Cancelled)
        instance.trackEvent(NotificationPresenter.class, "Download", "Download Content: Cancelled.");

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateSkip()
    {
        Timber.d("Event notified : skipped");

        builder.setContentIntent(getPausedIntent());
        builder.setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentInfo("")
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentTitle(res.getString(R.string.download_cancelled));

        // Tracking Event (Download Skipped)
        instance.trackEvent(NotificationPresenter.class, "Download", "Download Content: Skipped.");

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private PendingIntent getDefaultIntent() {
        Intent resultIntent = new Intent(instance, DownloadsActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Bundle bundle = new Bundle();
        bundle.putInt(Consts.DOWNLOAD_COUNT, HentoidApp.getDownloadCount());
        resultIntent.putExtras(bundle);

        return PendingIntent.getActivity(instance, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPausedIntent() {
        Intent resultIntent = new Intent(instance, QueueActivity.class);
        return PendingIntent.getActivity(instance, 0, resultIntent, PendingIntent.FLAG_ONE_SHOT);
    }

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
