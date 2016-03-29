package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Intent;
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
import me.devsaki.hentoid.components.ImageDownloadBatch;
import me.devsaki.hentoid.components.ImageDownloadTask;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.HitomiParser;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Download Manager implemented as a service
 */
public class DownloadService extends IntentService {

    public static final String INTENT_PERCENT_BROADCAST = "broadcast_percent";
    public static final String NOTIFICATION = "me.devsaki.hentoid.services";
    private static final String TAG = DownloadService.class.getName();
    public static boolean paused;
    private NotificationPresenter notificationPresenter;
    private HentoidDB db;
    private ExecutorService executorService;
    private Content currentContent;

    public DownloadService() {
        super(DownloadService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Download service created");

        notificationPresenter = new NotificationPresenter();
        db = new HentoidDB(this);
        executorService = Executors.newFixedThreadPool(2);
    }

    @Override
    public void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
        Log.i(TAG, "Download service destroyed");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkStatus.isOnline(this)) {
            Log.e(TAG, "No connection");
            return;
        }

        currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (currentContent != null && currentContent.getStatus() != StatusContent.DOWNLOADED) {

            notificationPresenter.downloadStarted(currentContent);
            if (paused) {
                interruptDownload();
                return;
            }
            try {
                parseImageFiles();
            } catch (Exception e) {
                currentContent.setStatus(StatusContent.UNHANDLED_ERROR);
                notificationPresenter.updateNotification(0);
                currentContent.setStatus(StatusContent.PAUSED);
                db.updateContentStatus(currentContent);
                updateActivity(-1);
                return;
            }

            if (paused) {
                interruptDownload();
                return;
            }

            Log.i(TAG, "Start Download Content : " + currentContent.getTitle());

            // Tracking Event (Download Added)
            HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                    "Download Content: Start.");

            boolean error = false;
            //Directory
            File dir = AndroidHelper.getDownloadDir(currentContent, this);
            try {
                //Download Cover Image
                executorService.submit(
                        new ImageDownloadTask(
                                dir, "thumb", currentContent.getCoverImageUrl()
                        )
                ).get();
            } catch (Exception e) {
                Log.e(TAG, "Error Saving cover image " + currentContent.getTitle(), e);
                error = true;
            }


            if (paused) {
                interruptDownload();
                if (currentContent.getStatus() == StatusContent.SAVED) {
                    try {
                        FileUtils.deleteDirectory(dir);
                    } catch (IOException e) {
                        Log.e(TAG, "error deleting content directory", e);
                    }
                }
                return;
            }

            List<ImageFile> imageFiles = currentContent.getImageFiles();
            ImageDownloadBatch downloadBatch = new ImageDownloadBatch(executorService);
            for (ImageFile imageFile : imageFiles) {
                if (imageFile.getStatus() != StatusContent.IGNORED) {
                    downloadBatch.addTask(
                            new ImageDownloadTask(
                                    dir, imageFile.getName(), imageFile.getUrl()
                            )
                    );
                }
            }

            int i = 0;
            for (ImageFile imageFile : imageFiles) {
                if (paused) {
                    interruptDownload();
                    downloadBatch.cancel();
                    if (currentContent.getStatus() == StatusContent.SAVED) {
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
                notificationPresenter.updateNotification(percent);
                updateActivity(percent);

                if (imageFileErrorDownload) {
                    imageFile.setStatus(StatusContent.ERROR);
                } else {
                    imageFile.setStatus(StatusContent.DOWNLOADED);
                }
                db.updateImageFileStatus(imageFile);
            }

            db.updateContentStatus(currentContent);
            currentContent.setDownloadDate(new Date().getTime());
            if (error) {
                currentContent.setStatus(StatusContent.ERROR);
            } else {
                currentContent.setStatus(StatusContent.DOWNLOADED);
            }
            //Save JSON file
            try {
                Helper.saveJson(currentContent, dir);
            } catch (IOException e) {
                Log.e(TAG, "Error Save JSON " + currentContent.getTitle(), e);
            }
            db.updateContentStatus(currentContent);
            Log.i(TAG, "Finish Download Content : " + currentContent.getTitle());
            notificationPresenter.updateNotification(0);
            updateActivity(-1);
            currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
            if (currentContent != null) {
                Intent intentService = new Intent(Intent.ACTION_SYNC, null, this,
                        DownloadService.class);
                intentService.putExtra("content_id", currentContent.getId());
                startService(intentService);
            }
        }
    }

    private void interruptDownload() {
        paused = false;
        currentContent = db.selectContentById(currentContent.getId());
        notificationPresenter.downloadInterrupted(currentContent);
    }

    private void updateActivity(double percent) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(INTENT_PERCENT_BROADCAST, percent);
        sendBroadcast(intent);
    }

    private void parseImageFiles() throws Exception {
        List<String> aUrls = new ArrayList<>();
        try {
            switch (currentContent.getSite()) {
                case HITOMI:
                    String html = HttpClientHelper.call(currentContent.getReaderUrl());
                    aUrls = HitomiParser.parseImageList(html);
                    break;
                case NHENTAI:
                    String json = HttpClientHelper.call(currentContent.getGalleryUrl() + "/json");
                    aUrls = NhentaiParser.parseImageList(json);
                    break;
                case TSUMINO:
                    aUrls = TsuminoParser.parseImageList(currentContent);
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
        currentContent.setImageFiles(imageFileList);
        db.insertImageFiles(currentContent);
    }
}