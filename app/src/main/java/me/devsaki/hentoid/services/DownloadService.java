package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Intent;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.parsers.ASMHentaiParser;
import me.devsaki.hentoid.parsers.HentaiCafeParser;
import me.devsaki.hentoid.parsers.HitomiParser;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Download Manager implemented as a service
 * <p/>
 * TODO: Implement download job tracking: https://github.com/AVnetWS/Hentoid/issues/110
 * 1 image = 1 task, n images = 1 chapter = 1 job = 1 bundled task.
 */
public class DownloadService extends IntentService {
    private static final String TAG = LogHelper.makeLogTag(DownloadService.class);

    public static boolean paused;
    private Content currentContent;
    private HentoidDB db;
    private NotificationPresenter notificationPresenter;

    public DownloadService() {
        super(DownloadService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = HentoidDB.getInstance(this);

        notificationPresenter = new NotificationPresenter();
        EventBus.getDefault().register(notificationPresenter);

        LogHelper.d(TAG, "Download service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(notificationPresenter);
        notificationPresenter = null;

        LogHelper.d(TAG, "Download service destroyed");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkStatus.isOnline(this)) {
            LogHelper.w(TAG, "No connection!");
            return;
        }

        // TODO: Link with FileHelper for SAF safe method
        currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (currentContent != null && currentContent.getStatus() != StatusContent.DOWNLOADED) {
            initDownload();

            File dir = FileHelper.getContentDownloadDir(this, currentContent);
            // If the download directory already has files,
            // then we simply delete them, since this points to a failed download
            // This includes in progress downloads, that were paused, then resumed.
            // So technically, we are downloading everything once again.
            // This is required for ImageDownloadBatch to not hang on a download.
            FileHelper.rmDir(dir);

            ImageDownloadBatch downloadBatch = new ImageDownloadBatch();
            addTask(dir, downloadBatch);

            queryForAdditionalDownloads();
        }
    }

    // TODO: Link with FileHelper for SAF safe method
    private void addTask(File dir, ImageDownloadBatch downloadBatch) {
        // Add download tasks
        downloadBatch.newTask(dir, "thumb", currentContent.getCoverImageUrl());
        List<ImageFile> imageFiles = currentContent.getImageFiles();
        for (int i = 0; i < imageFiles.size() && !paused; i++) {
            ImageFile imageFile = imageFiles.get(i);
            downloadBatch.newTask(dir, imageFile.getName(), imageFile.getUrl());
            downloadBatch.waitForOneCompletedTask();
            double percent = (i + 1) * 100.0 / imageFiles.size();
            updateActivity(percent);
        }

        if (paused) {
            LogHelper.d(TAG, "Pause requested");
            interruptDownload();
            downloadBatch.cancelAllTasks();
            if (currentContent.getStatus() == StatusContent.CANCELED) {
                notificationPresenter.downloadInterrupted(currentContent);
            }

            return;
        }

        // Assign status tag to ImageFile(s)
        short errorCount = downloadBatch.getErrorCount();
        for (ImageFile imageFile : currentContent.getImageFiles()) {
            if (errorCount > 0) {
                imageFile.setStatus(StatusContent.ERROR);
                errorCount--;
            } else {
                imageFile.setStatus(StatusContent.DOWNLOADED);
            }
            db.updateImageFileStatus(imageFile);
        }
        // Assign status tag to Content, add timestamp, and save to db
        if (downloadBatch.hasError()) {
            currentContent.setStatus(StatusContent.ERROR);
        } else {
            currentContent.setStatus(StatusContent.DOWNLOADED);
        }
        currentContent.setDownloadDate(new Date().getTime());
        db.updateContentStatus(currentContent);

        postDownloadCompleted(dir);
    }

    private void initDownload() {
        notificationPresenter.downloadStarted(currentContent);
        if (paused) {
            interruptDownload();
            return;
        }
        try {
            parseImageFiles();
        } catch (Exception e) {
            currentContent.setStatus(StatusContent.UNHANDLED_ERROR);
            currentContent.setStatus(StatusContent.PAUSED);
            db.updateContentStatus(currentContent);
            updateActivity(-1);

            return;
        }

        if (paused) {
            interruptDownload();
            return;
        }
        LogHelper.d(TAG, "Content download started: " + currentContent.getTitle());

        // Tracking Event (Download Added)
        HentoidApp.getInstance().trackEvent("Download Service", "Download",
                "Download Content: Start.");
    }

    private void postDownloadCompleted(File dir) {
        // Save JSON file
        try {
            JsonHelper.saveJson(currentContent, dir);
        } catch (IOException e) {
            LogHelper.e(TAG, "Error saving JSON: " + currentContent.getTitle(), e);
        }

        HentoidApp.downloadComplete();
        updateActivity(-1);
        LogHelper.d(TAG, "Content download finished: " + currentContent.getTitle());
    }

    private void queryForAdditionalDownloads() {
        // Search for queued content download tasks and fire intent
        currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (currentContent != null) {
            Intent intentService = new Intent(Intent.ACTION_SYNC, null, this,
                    DownloadService.class);
            intentService.putExtra("content_id", currentContent.getId());
            startService(intentService);
        }
    }

    private void interruptDownload() {
        paused = false;
        currentContent = db.selectContentById(currentContent.getId());
        notificationPresenter.downloadInterrupted(currentContent);
    }

    private void updateActivity(double percent) {
        EventBus.getDefault().post(new DownloadEvent(percent));
    }

    private void parseImageFiles() {
        List<String> aUrls = new ArrayList<>();
        switch (currentContent.getSite()) {
            case ASMHENTAI:
                aUrls = ASMHentaiParser.parseImageList(currentContent);
                break;
            case HENTAICAFE:
                aUrls = HentaiCafeParser.parseImageList(currentContent);
                break;
            case HITOMI:
                aUrls = HitomiParser.parseImageList(currentContent);
                break;
            case NHENTAI:
                aUrls = NhentaiParser.parseImageList(currentContent);
                break;
            case TSUMINO:
                aUrls = TsuminoParser.parseImageList(currentContent);
                break;
            default:
                break;
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
