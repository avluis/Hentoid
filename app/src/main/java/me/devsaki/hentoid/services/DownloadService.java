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
import me.devsaki.hentoid.parsers.PururinParser;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.NetworkStatus;
import timber.log.Timber;

/**
 * Download Manager implemented as a service
 * <p/>
 * TODO: Implement download job tracking: https://github.com/AVnetWS/Hentoid/issues/110
 * 1 image = 1 task, n images = 1 chapter = 1 job = 1 bundled task.
 */
public class DownloadService extends IntentService {

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

        Timber.d("Download service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(notificationPresenter);
        notificationPresenter = null;

        Timber.d("Download service destroyed");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkStatus.isOnline(this)) {
            Timber.w("No connection!");
            return;
        }

        currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (currentContent != null && currentContent.getStatus() != StatusContent.DOWNLOADED) {
            initDownload();

            File dir = FileHelper.getContentDownloadDir(this, currentContent);
            Timber.d("Content Download Dir; %s", dir);
            Timber.d("Directory created: %s", FileHelper.createDirectory(dir));

            String fileRoot = FileHelper.getRoot();
            currentContent.setStorageFolder(dir.getAbsolutePath().substring(fileRoot.length()));
            db.updateContentStorageFolder(currentContent);

            ImageDownloadBatch downloadBatch = new ImageDownloadBatch();
            addTask(dir, downloadBatch);

            queryForAdditionalDownloads();
        }
    }

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
            Timber.d("Pause requested");
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

            Timber.e(e, "Exception while parsing image files");

            return;
        }

        if (paused) {
            interruptDownload();
            return;
        }
        Timber.d("Content download started: %s", currentContent.getTitle());

        // Tracking Event (Download Added)
        HentoidApp.getInstance().trackEvent(DownloadService.class, "Download", "Download Content: Start");
    }

    private void postDownloadCompleted(File dir) {
        // Save JSON file
        try {
            JsonHelper.saveJson(currentContent, dir);
        } catch (IOException e) {
            Timber.e(e, "Error saving JSON: %s", currentContent.getTitle());
        }

        HentoidApp.downloadComplete();
        updateActivity(-1);
        Timber.d("Content download finished: %s", currentContent.getTitle());

        // Tracking Event (Download Completed)
        HentoidApp.getInstance().trackEvent(DownloadService.class, "Download", "Download Content: Complete");
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

    // TODO: Implement null handling as fail/retry state
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
            case PURURIN:
                aUrls = PururinParser.parseImageList(currentContent);
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
