package me.devsaki.hentoid.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.components.ImageDownloadBatch;
import me.devsaki.hentoid.components.ImageDownloadTask;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.parser.HitomiParser;
import me.devsaki.hentoid.parser.NhentaiParser;
import me.devsaki.hentoid.parser.TsuminoParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Download Manager implemented as a service
 * TODO: Reset notification when a download is paused (when there are multiple downloads).
 */
public class DownloadService extends IntentService {

    public static final String INTENT_PERCENT_BROADCAST = "broadcast_percent";
    public static final String NOTIFICATION = "me.devsaki.hentoid.service";
    private static final String TAG = DownloadService.class.getName();
    public static boolean paused;
    private static int downloadCount = 0;
    private NotificationManager notificationManager;
    private HentoidDB db;
    private ExecutorService executorService;

    public DownloadService() {
        super(DownloadService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        db = new HentoidDB(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        executorService = Executors.newFixedThreadPool(2);

        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    @Override
    public void onDestroy() {
        downloadCount = 0;
        executorService.shutdown();
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkStatus.isOnline(this)) {
            Log.e(TAG, "No connection");
            return;
        }

        Content content = db.selectContentByStatus(StatusContent.DOWNLOADING);

        downloadCount++;

        if (content != null && content.getStatus() != StatusContent.DOWNLOADED) {

            showNotification(0, content);

            if (paused) {
                interruptDownload(content);
                return;
            }
            try {
                parseImageFiles(content);
            } catch (Exception e) {
                content.setStatus(StatusContent.UNHANDLED_ERROR);
                showNotification(0, content);
                content.setStatus(StatusContent.PAUSED);
                db.updateContentStatus(content);
                updateActivity(-1);
                return;
            }

            if (paused) {
                interruptDownload(content);
                return;
            }

            Log.i(TAG, "Start Download Content : " + content.getTitle());

            // Tracking Event (Download Added)
            HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                    "Download Content: Start.");

            boolean error = false;
            //Directory
            File dir = Helper.getDownloadDir(content, this);
            try {
                //Download Cover Image
                executorService.submit(new ImageDownloadTask(
                        dir, "thumb.jpg", content.getCoverImageUrl()
                )).get();
            } catch (Exception e) {
                Log.e(TAG, "Error Saving cover image " + content.getTitle(), e);
                error = true;
            }


            if (paused) {
                interruptDownload(content);
                if (content.getStatus() == StatusContent.SAVED) {
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        Log.e(TAG, "error deleting content directory", e);
                    }
                }
                return;
            }

            List<ImageFile> imageFiles = content.getImageFiles();
            ImageDownloadBatch downloadBatch = new ImageDownloadBatch(executorService);
            for (ImageFile imageFile : imageFiles) {
                if (imageFile.getStatus() != StatusContent.IGNORED) {
                    downloadBatch.addTask(dir, imageFile.getName(), imageFile.getUrl());
                }
            }

            int i = 0;
            for (ImageFile imageFile : imageFiles) {
                if (paused) {
                    interruptDownload(content);
                    downloadBatch.cancel();
                    if (content.getStatus() == StatusContent.SAVED) {
                        try {
                            FileUtils.deleteDirectory(dir);
                        } catch (IOException e) {
                            Log.e(TAG, "error deleting content directory", e);
                        }
                    }
                    return;
                }
                if (!NetworkStatus.isOnline(this)) {
                    Log.e(TAG, "No connection");
                    downloadBatch.cancel();
                    return;
                }
                boolean imageFileErrorDownload = false;
                try {
                    downloadBatch.waitForCompletedTask();
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading image file");
                    error = true;
                    imageFileErrorDownload = true;
                }
                i++;
                double percent = i * 100.0 / imageFiles.size();
                showNotification(percent, content);
                updateActivity(percent);

                if (imageFileErrorDownload) {
                    imageFile.setStatus(StatusContent.ERROR);
                } else {
                    imageFile.setStatus(StatusContent.DOWNLOADED);
                }
                db.updateImageFileStatus(imageFile);
            }

            db.updateContentStatus(content);
            content.setDownloadDate(new Date().getTime());
            if (error) {
                content.setStatus(StatusContent.ERROR);
            } else {
                content.setStatus(StatusContent.DOWNLOADED);
            }
            //Save JSON file
            try {
                Helper.saveJson(content, dir);
            } catch (IOException e) {
                Log.e(TAG, "Error Save JSON " + content.getTitle(), e);
            }
            db.updateContentStatus(content);
            Log.i(TAG, "Finish Download Content : " + content.getTitle());
            showNotification(0, content);
            updateActivity(-1);
            content = db.selectContentByStatus(StatusContent.DOWNLOADING);
            if (content != null) {
                Intent intentService = new Intent(Intent.ACTION_SYNC, null, this,
                        DownloadService.class);
                intentService.putExtra("content_id", content.getId());
                startService(intentService);
            }
        }
    }

    private void interruptDownload(Content content) {
        paused = false;
        content = db.selectContentById(content.getId());
        showNotification(0, content);
    }

    private void updateActivity(double percent) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(INTENT_PERCENT_BROADCAST, percent);
        sendBroadcast(intent);
    }

    private void showNotification(double percent, Content content) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(content.getSite().getIco())
                .setContentTitle(content.getTitle());

        int resource = 0;
        mBuilder.setLocalOnly(true);

        Intent resultIntent = null;
        switch (content.getStatus()) {
            case DOWNLOADED:
            case ERROR:
            case UNHANDLED_ERROR:
                resultIntent = new Intent(this, DownloadsActivity.class);
                break;
            case DOWNLOADING:
            case PAUSED:
                resultIntent = new Intent(this, QueueActivity.class);
                break;
            case SAVED:
                resultIntent = new Intent(this, content.getWebActivityClass());
                resultIntent.putExtra("url", content.getUrl());
                break;
        }

        // Adds the Intent to the top of the stack
        // Gets a PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this,
                0, resultIntent, PendingIntent.FLAG_ONE_SHOT);

        int notificationID = 0;
        if (content.getStatus() == StatusContent.DOWNLOADED && downloadCount > 1) {
            mBuilder.setContentTitle(getApplicationContext().getString(R.string.app_name));
            String text = (downloadCount + " chapters downloaded.");
            mBuilder.setContentText(text);
            notify(mBuilder, notificationID, percent, resultPendingIntent);
            return;
        }
        if (content.getStatus() != StatusContent.DOWNLOADING) {
            switch (content.getStatus()) {
                case DOWNLOADED:
                    resource = R.string.download_completed;
                    // Tracking Event (Download Completed)
                    HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                            "Download Content: Success.");
                    break;
                case PAUSED:
                    resource = R.string.download_paused;
                    break;
                case SAVED:
                    resource = R.string.download_cancelled;
                    // Tracking Event (Download Cancelled)
                    HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                            "Download Content: Cancelled.");
                    break;
                case ERROR:
                    resource = R.string.download_error;
                    // Tracking Event (Download Error)
                    HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                            "Download Content: Error.");
                    break;
                case UNHANDLED_ERROR:
                    resource = R.string.unhandled_download_error;
                    // Tracking Event (Download Unhandled Error)
                    HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                            "Download Content: Unhandled Error.");
                    break;
            }
            mBuilder.setContentText(getResources().getString(resource));
            mBuilder.setProgress(0, 0, false);

        } else {
            mBuilder.setContentText(getResources().getString(R.string.downloading)
                    + String.format(Locale.US, " %.2f", percent) + "%");
            mBuilder.setProgress(100, (int) percent, percent == 0);
        }
        notify(mBuilder, notificationID, percent, resultPendingIntent);
    }

    private void notify(NotificationCompat.Builder mBuilder, int notificationID, double percent,
                        PendingIntent resultPendingIntent) {
        Notification notification = mBuilder.build();
        notification.contentIntent = resultPendingIntent;
        if (percent > 0) {
            notification.flags = Notification.FLAG_ONGOING_EVENT;
        } else {
            notification.flags = notification.flags | Notification.DEFAULT_LIGHTS
                    | Notification.FLAG_AUTO_CANCEL;
        }
        notificationManager.notify(notificationID, notification);
    }

    private void parseImageFiles(Content content) throws Exception {
        List<String> aUrls = new ArrayList<>();
        try {
            switch (content.getSite()) {
                case HITOMI:
                    String html = HttpClientHelper.call(content.getReaderUrl());
                    aUrls = HitomiParser.parseImageList(html);
                    break;
                case NHENTAI:
                    String json = HttpClientHelper.call(content.getGalleryUrl() + "/json");
                    aUrls = NhentaiParser.parseImageList(json);
                    break;
                case TSUMINO:
                    aUrls = TsuminoParser.parseImageList(content);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting image urls", e);
            throw e;
        }

        int i = 1;
        List<ImageFile> imageFileList = new ArrayList<>();
        for (String str : aUrls) {
            String name = String.format(Locale.US, "%03d", i);
            imageFileList.add(new ImageFile()
                    .setUrl(str)
                    .setOrder(i++)
                    .setStatus(StatusContent.SAVED)
                    .setName(name));
        }
        content.setImageFiles(imageFileList);
        db.insertImageFiles(content);
    }
}