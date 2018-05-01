package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Intent;
import android.util.Pair;

import com.android.volley.Request;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.parsers.ContentParser;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.MimeTypes;
import me.devsaki.hentoid.util.NetworkStatus;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

public class ContentDownloadService extends IntentService {

    private HentoidDB db;
    private NotificationPresenter notificationPresenter;
    private boolean downloadCanceled;
    private boolean downloadSkipped;
    private boolean downloadPaused;

    public ContentDownloadService() {
        super(ContentDownloadService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = HentoidDB.getInstance(this);

        notificationPresenter = new NotificationPresenter();
        EventBus.getDefault().register(notificationPresenter);
        EventBus.getDefault().register(this);

        Timber.d("Download service created");
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(notificationPresenter);
        notificationPresenter = null;

        Timber.d("Download service destroyed");
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkStatus.isOnline(this)) {
            Timber.w("No connection!");
            return;
        }

        Timber.d("New intent processed");

        downloadQueueHead();
    }

    private void downloadQueueHead() {
/*
        // Exits if download queue is already running - there can only be one service active at a time
        if (!QueueManager.getInstance(this).isQueueEmpty()) {
            Timber.d("Download still active. Aborting");
            return;
        }
*/

        // Works on first item of queue
        List<Pair<Integer, Integer>> queue = db.selectQueue();
        if (0 == queue.size()) {
            Timber.w("Queue is empty. Aborting.");
            return;
        }

        Content content = db.selectContentById(queue.get(0).first);

        if (null == content || StatusContent.DOWNLOADED == content.getStatus()) {
            Timber.w("Content is unavailable, or already downloaded. Aborting.");
            return;
        }

        // Check if images are already known
        List<ImageFile> images = content.getImageFiles();
        if (0 == images.size()) {
            // Create image list in DB
            images = parseImageFiles(content);
            content.setImageFiles(images);
            db.insertImageFiles(content);
        }

        if (0 == images.size()) {
            Timber.w("Image list is empty");
            return;
        }

        Timber.d("Downloading '%s' [%s]", content.getTitle(), content.getId());
        downloadCanceled = false;
        downloadSkipped = false;
        downloadPaused = false;
        notificationPresenter.downloadStarted(content);
        File dir = FileHelper.getContentDownloadDir(this, content);
        Timber.d("Directory created: %s", FileHelper.createDirectory(dir));

        String fileRoot = Preferences.getRootFolderName();
        content.setStorageFolder(dir.getAbsolutePath().substring(fileRoot.length()));
        db.updateContentStorageFolder(content);

        // Plan download actions
        ImageFile cover = new ImageFile().setName("thumb").setUrl(content.getCoverImageUrl());
        QueueManager.getInstance(this).addToRequestQueue(buildStringRequest(cover, dir));
        for (ImageFile img : images) {
            if (img.getStatus().equals(StatusContent.SAVED) || img.getStatus().equals(StatusContent.ERROR))
                QueueManager.getInstance(this).addToRequestQueue(buildStringRequest(img, dir));
        }

        // Watches progression
        // NB : download pause is managed at the Volley queue level (see QueueManager.pauseQueue / startQueue)
        double dlRate = 0;
        while (dlRate < 1 && !downloadCanceled && !downloadSkipped && !downloadPaused) {
            int pagesOK = db.countProcessedImagesById(content.getId(), new int[]{StatusContent.DOWNLOADED.getCode()});
            int pagesKO = db.countProcessedImagesById(content.getId(), new int[]{StatusContent.ERROR.getCode()});
            dlRate = (pagesOK + pagesKO) * 1.0 / images.size();
            updateActivity(pagesOK, pagesKO, images.size());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (!downloadCanceled && !downloadSkipped && !downloadPaused) {
            // Save JSON file
            try {
                JsonHelper.saveJson(content, dir);
            } catch (IOException e) {
                Timber.e(e, "Error saving JSON: %s", content.getTitle());
            }

            // Signal activity end
            HentoidApp.downloadComplete();
            Timber.d("Content download finished: %s [%s]", content.getTitle(), content.getId());

            // Tracking Event (Download Completed)
            HentoidApp.getInstance().trackEvent(ContentDownloadService.class, "Download", "Download Content: Complete");

            // Mark content as downloaded
            boolean isSuccess = (0 == db.countProcessedImagesById(content.getId(), new int[]{StatusContent.ERROR.getCode(), StatusContent.IGNORED.getCode()}));
            content.setDownloadDate(new Date().getTime());
            content.setStatus(isSuccess ? StatusContent.DOWNLOADED : StatusContent.ERROR);
            db.updateContentStatus(content);

            completeActivity(content, isSuccess);

            // Delete from queue
            db.deleteQueueById(content.getId());
        } else if (downloadCanceled) {
            Timber.d("Content download canceled: %s [%s]", content.getTitle(), content.getId());
        } else if (downloadSkipped) {
            Timber.d("Content download skipped : %s [%s]", content.getTitle(), content.getId());
        } else if (downloadPaused) {
            Timber.d("Content download paused : %s [%s]", content.getTitle(), content.getId());
        }

        if (!downloadPaused) {
            // Download next content in a new Intent
            Intent intentService = new Intent(Intent.ACTION_SYNC, null, this, ContentDownloadService.class);
            startService(intentService);
        }

        EventBus.getDefault().unregister(this);
    }

    private static List<ImageFile> parseImageFiles(Content content) {
        ContentParser parser = ContentParserFactory.getInstance().getParser(content);
        List<String> aUrls = parser.parseImageList(content);

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

        return imageFileList;
    }

    private InputStreamVolleyRequest buildStringRequest(ImageFile img, File dir) {
        return new InputStreamVolleyRequest(Request.Method.GET, img.getUrl(),
                response -> {
                    try {
                        if (response != null) {
                            saveImage(img, dir, response);
                            finalizeImage(img, true);
                        }
                    } catch (IOException e) {
                        Timber.d("I/O error - Image %s not saved", img.getUrl());
                        e.printStackTrace();
                        finalizeImage(img, false);
                    }
                }, error -> {
            Timber.d("Download error - Image %s not retrieved", img.getUrl());
            error.printStackTrace();
            finalizeImage(img, false);
        }, null);
    }

    private static void saveImage(ImageFile img, File dir, Map.Entry<byte[], Map<String, String>> response) throws IOException {
        // Create a file on desired path and write stream data to it
        String contentType = response.getValue().get("Content-Type");
        File file = new File(dir, img.getName() + "." + MimeTypes.getExtensionFromMimeType(contentType));
        Timber.d("Write image %s to %s", img.getUrl(), file.getPath());

        byte data[] = new byte[1024];
        int count;

        try (InputStream input = new ByteArrayInputStream(response.getKey())) {
            try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {

                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }

                output.flush();
            }
        }
    }

    private void finalizeImage(ImageFile img, boolean success) {
        img.setStatus(success ? StatusContent.DOWNLOADED : StatusContent.ERROR);
        db.updateImageFileStatus(img);
    }

    private static void updateActivity(int pagesOK, int pagesKO, int totalPages) {
        Timber.d("UpdateActivity : OK : %s - KO : %s - Total : %s > %s pc.", pagesOK, pagesKO, totalPages, String.valueOf((pagesOK + pagesKO) * 100.0 / totalPages));
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PROGRESS, pagesOK, pagesKO, totalPages));
    }

    private static void completeActivity(Content content, boolean success) {
        Timber.d("CompleteActivity : success = %s", String.valueOf(success));
        EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE));
    }

    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            // Nothing special in case of progress
            // case DownloadEvent.EV_PROGRESS:
            case DownloadEvent.EV_PAUSE:
/*
                QueueManager.getInstance().pauseQueue();
*/
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                QueueManager.getInstance().cancelQueue();
                downloadPaused = true;
                break;
            // Won't be here to act
/*
            case DownloadEvent.EV_UNPAUSE :
                // QueueManager.getInstance().startQueue();
                db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
                break;
*/
            case DownloadEvent.EV_CANCEL:
                QueueManager.getInstance().cancelQueue();
                downloadCanceled = true;
                break;
            case DownloadEvent.EV_SKIP:
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                QueueManager.getInstance().cancelQueue();
                downloadSkipped = true;
                break;
        }
    }
}
