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
import me.devsaki.hentoid.parsers.BaseParser;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.NetworkStatus;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Download Manager implemented as a service
 * <p/>
 * TODO: Implement download job tracking: https://github.com/avluis/Hentoid/issues/110
 * 1 image = 1 task, n images = 1 chapter = 1 job = 1 bundled task.
 */
@Deprecated
public class DownloadService extends IntentService {

    public static boolean paused;
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

        Content currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (currentContent != null && currentContent.getStatus() != StatusContent.DOWNLOADED) {
            initDownload(currentContent);

            File dir = FileHelper.getContentDownloadDir(this, currentContent);
            Timber.d("Content Download Dir; %s", dir);
            Timber.d("Directory created: %s", FileHelper.createDirectory(dir));

            String fileRoot = Preferences.getRootFolderName();
            currentContent.setStorageFolder(dir.getAbsolutePath().substring(fileRoot.length()));
            db.updateContentStorageFolder(currentContent);

            ImageDownloadBatch downloadBatch = new ImageDownloadBatch();
            addTask(dir, downloadBatch, currentContent);

            queryForAdditionalDownloads();
        }
    }

    private void addTask(File dir, ImageDownloadBatch downloadBatch, Content currentContent) {
        Timber.d("addTask %s (%s)", currentContent.getTitle(), currentContent.getId());
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
            interruptDownload(currentContent.getId());
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

        postDownloadCompleted(dir, currentContent);
    }

    private void initDownload(Content currentContent) {
        notificationPresenter.downloadStarted(currentContent);
        if (paused) {
            interruptDownload(currentContent.getId());
            return;
        }
        try {
            parseImageFiles(currentContent);
        } catch (Exception e) {
            currentContent.setStatus(StatusContent.UNHANDLED_ERROR);
            currentContent.setStatus(StatusContent.PAUSED);
            db.updateContentStatus(currentContent);
            updateActivity(-1);

            Timber.e(e, "Exception while parsing image files");

            return;
        }

        if (paused) {
            interruptDownload(currentContent.getId());
            return;
        }
        Timber.d("Content download started: %s", currentContent.getTitle());

        // Tracking Event (Download Added)
        HentoidApp.getInstance().trackEvent(DownloadService.class, "Download", "Download Content: Start");
    }

    private void postDownloadCompleted(File dir, Content currentContent) {
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
        Content currentContent = db.selectContentByStatus(StatusContent.DOWNLOADING);
        if (currentContent != null) {
            Intent intentService = new Intent(Intent.ACTION_SYNC, null, this,
                    DownloadService.class);
            intentService.putExtra("content_id", currentContent.getId());
            startService(intentService);
        }
    }

    private void interruptDownload(int contentId) {
        paused = false;
        Content currentContent = db.selectContentById(contentId);
        notificationPresenter.downloadInterrupted(currentContent);
    }

    private void updateActivity(double percent) {
        EventBus.getDefault().post(new DownloadEvent(percent));
    }

    // TODO: Implement null handling as fail/retry state
    private void parseImageFiles(Content currentContent) {
        ContentParser parser = ContentParserFactory.getInstance().getParser(currentContent);
        List<String> aUrls = parser.parseImageList(currentContent);

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
