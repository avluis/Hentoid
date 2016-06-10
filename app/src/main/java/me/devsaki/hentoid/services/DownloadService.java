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
import me.devsaki.hentoid.listener.DownloadEvent;
import me.devsaki.hentoid.parsers.HitomiParser;
import me.devsaki.hentoid.parsers.NhentaiParser;
import me.devsaki.hentoid.parsers.TsuminoParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.NetworkStatus;

/**
 * Download Manager implemented as a service
 * <p/>
 * TODO: Implement download job tracking:
 * 1 image = 1 task, n images = 1 chapter = 1 job = 1 bundled task.
 */
public class DownloadService extends IntentService {
    private static final String TAG = LogHelper.makeLogTag(DownloadService.class);

    public static boolean paused;
    private NotificationPresenter notificationPresenter;
    private HentoidDB db;
    private Content currentContent;

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

            // Initialize
            File dir = Helper.getContentDownloadDir(this, currentContent);

            // If the download directory already has files,
            // then we simply delete them, since this points to a failed download
            // This includes in progress downloads, that were paused, then resumed.
            // So technically, we are downloading everything once again.
            // This is required for ImageDownloadBatch to not hang on a download.
            Helper.cleanDir(dir);

            ImageDownloadBatch downloadBatch = new ImageDownloadBatch();

            // Add download tasks
            downloadBatch.newTask(dir, "thumb", currentContent.getCoverImageUrl());
            do {
                for (ImageFile imageFile : currentContent.getImageFiles()) {
                    downloadBatch.newTask(dir, imageFile.getName(), imageFile.getUrl());
                }
            } while (false);

            // Track and wait for download to complete
            final int qtyPages = currentContent.getQtyPages();
            for (int i = 0; i <= qtyPages; ++i) {

                if (paused) {
                    LogHelper.d(TAG, "Interrupt!!");
                    interruptDownload();
                    downloadBatch.cancelAllTasks();

                    if (currentContent.getStatus() == StatusContent.CANCELED) {
                        // Update notification
                        notificationPresenter.downloadInterrupted(currentContent);
                    }

                    return;
                }

                downloadBatch.waitForOneCompletedTask();
                double percent = i * 100.0 / qtyPages;
                updateActivity(percent);
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

            // Save JSON file
            try {
                JsonHelper.saveJson(currentContent, dir);
            } catch (IOException e) {
                LogHelper.e(TAG, "Error saving JSON: " + currentContent.getTitle(), e);
            }

            HentoidApp.downloadComplete();
            updateActivity(-1);
            LogHelper.d(TAG, "Content download finished: " + currentContent.getTitle());

            // Search for queued content download tasks and fire intent
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
        EventBus.getDefault().post(new DownloadEvent(percent));
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
            LogHelper.e(TAG, "Error getting image urls: ", e);
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