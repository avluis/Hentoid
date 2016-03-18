package me.devsaki.hentoid.service;

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
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.parser.HitomiParser;
import me.devsaki.hentoid.parser.NhentaiParser;
import me.devsaki.hentoid.parser.TsuminoParser;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Download Manager implemented as a service
 */
public class DownloadService extends IntentService {

    public static final String INTENT_PERCENT_BROADCAST = "broadcast_percent";
    public static final String NOTIFICATION = "me.devsaki.hentoid.service";
    private static final String TAG = DownloadService.class.getName();
    public static boolean paused;
    private NotificationPresenter notificationPresenter;
    private HentoidDB db;
    private ExecutorService executorService;

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

        Content content = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (content != null && content.getStatus() != StatusContent.DOWNLOADED) {

            notificationPresenter.downloadStarted(content);
            if (paused) {
                interruptDownload();
                return;
            }
            try {
                parseImageFiles(content);
            } catch (Exception e) {
                content.setStatus(StatusContent.UNHANDLED_ERROR);
                notificationPresenter.updateNotification(0);
                content.setStatus(StatusContent.PAUSED);
                db.updateContentStatus(content);
                updateActivity(-1);
                return;
            }

            if (paused) {
                interruptDownload();
                return;
            }

            Log.i(TAG, "Start Download Content : " + content.getTitle());

            // Tracking Event (Download Added)
            HentoidApplication.getInstance().trackEvent("Download Service", "Download",
                    "Download Content: Start.");

            boolean error = false;
            //Directory
            File dir = AndroidHelper.getDownloadDir(content, this);
            try {
                //Download Cover Image
                executorService.submit(
                        new ImageDownloadTask(
                                dir, "thumb", content.getCoverImageUrl()
                        )
                ).get();
            } catch (Exception e) {
                Log.e(TAG, "Error Saving cover image " + content.getTitle(), e);
                error = true;
            }


            if (paused) {
                interruptDownload();
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
                notificationPresenter.updateNotification(percent);
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
            notificationPresenter.updateNotification(0);
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

    private void interruptDownload() {
        paused = false;
        notificationPresenter.updateNotification(0);
    }

    private void updateActivity(double percent) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(INTENT_PERCENT_BROADCAST, percent);
        sendBroadcast(intent);
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