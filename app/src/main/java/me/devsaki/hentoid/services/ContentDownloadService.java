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


/**
 * Created by Robb_w on 2018/04
 * Book download service; 1 instance everytime a new book of the queue has to be downloaded
 * NB : As per IntentService behaviour, only one thread can be active at a time (no parallel runs of ContentDownloadService)
 */
public class ContentDownloadService extends IntentService {

    private HentoidDB db;                                   // Hentoid database
    private NotificationPresenter notificationPresenter;    // Link to the notification presenter
    private boolean downloadCanceled;                       // True if a Cancel event has been processed; false by default
    private boolean downloadSkipped;                        // True if a Skip event has been processed; false by default

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
        EventBus.getDefault().unregister(this);
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

        downloadFirstInQueue();
    }

    /**
     * Start the download of the 1st book of the download queue
     */
    private void downloadFirstInQueue() {
        ContentQueueManager contentQueueManager = ContentQueueManager.getInstance();

        // Check if queue is already paused
        if (contentQueueManager.isQueuePaused()) {
            Timber.w("Queue is paused. Aborting download.");
            return;
        }

        // Works on first item of queue
        List<Pair<Integer, Integer>> queue = db.selectQueue();
        if (0 == queue.size()) {
            Timber.w("Queue is empty. Aborting download.");
            return;
        }

        Content content = db.selectContentById(queue.get(0).first);

        if (null == content || StatusContent.DOWNLOADED == content.getStatus()) {
            Timber.w("Content is unavailable, or already downloaded. Aborting download.");
            return;
        }

        content.setStatus(StatusContent.DOWNLOADING);
        db.updateContentStatus(content);

        // Check if images are already known
        List<ImageFile> images = content.getImageFiles();
        if (0 == images.size()) {
            // Create image list in DB
            images = fetchImageFiles(content);
            content.setImageFiles(images);
            db.insertImageFiles(content);
        }

        if (0 == images.size()) {
            Timber.w("Image list is empty. Aborting download.");
            return;
        }

        // Tracking Event (Download Added)
        HentoidApp.getInstance().trackEvent(ContentDownloadService.class, "Download", "Download Content: Start");

        Timber.d("Downloading '%s' [%s]", content.getTitle(), content.getId());
        downloadCanceled = false;
        downloadSkipped = false;
        notificationPresenter.downloadStarted(content);
        File dir = FileHelper.getContentDownloadDir(this, content);
        Timber.d("Directory created: %s", FileHelper.createDirectory(dir));

        String fileRoot = Preferences.getRootFolderName();
        content.setStorageFolder(dir.getAbsolutePath().substring(fileRoot.length()));
        db.updateContentStorageFolder(content);

        // Queue image download requests
        ImageFile cover = new ImageFile().setName("thumb").setUrl(content.getCoverImageUrl());
        RequestQueueManager.getInstance(this).addToRequestQueue(buildStringRequest(cover, dir));
        for (ImageFile img : images) {
            if (img.getStatus().equals(StatusContent.SAVED) || img.getStatus().equals(StatusContent.ERROR))
                RequestQueueManager.getInstance(this).addToRequestQueue(buildStringRequest(img, dir));
        }

        // Watch progression
        // NB : download pause is managed at the Volley queue level (see RequestQueueManager.pauseQueue / startQueue)
        double dlRate;
        int pagesOK, pagesKO;
        do {
            pagesOK = db.countProcessedImagesById(content.getId(), new int[]{StatusContent.DOWNLOADED.getCode()});
            pagesKO = db.countProcessedImagesById(content.getId(), new int[]{StatusContent.ERROR.getCode()});
            dlRate = (pagesOK + pagesKO) * 1.0 / images.size();
            notifyProgress(pagesOK, pagesKO, images.size());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (dlRate < 1 && !downloadCanceled && !downloadSkipped && !contentQueueManager.isQueuePaused());

        if (!downloadCanceled && !downloadSkipped && !contentQueueManager.isQueuePaused()) { // Download properly completed
            // Save JSON file
            try {
                JsonHelper.saveJson(content, dir);
            } catch (IOException e) {
                Timber.e(e, "Error saving JSON: %s", content.getTitle());
            }

            // Mark content as downloaded
            content.setDownloadDate(new Date().getTime());
            content.setStatus((0 == pagesKO) ? StatusContent.DOWNLOADED : StatusContent.ERROR);
            db.updateContentStatus(content);

            Timber.d("Content download finished: %s [%s]", content.getTitle(), content.getId());

            // Delete book from queue
            db.deleteQueueById(content.getId());

            // Signals current download as completed
            notifyComplete(pagesOK, pagesKO, images.size());

            // Increase downloads count
            contentQueueManager.downloadComplete();

            // Tracking Event (Download Completed)
            HentoidApp.getInstance().trackEvent(ContentDownloadService.class, "Download", "Download Content: Complete");
        } else if (downloadCanceled) {
            Timber.d("Content download canceled: %s [%s]", content.getTitle(), content.getId());
        } else if (downloadSkipped) {
            Timber.d("Content download skipped : %s [%s]", content.getTitle(), content.getId());
        } else if (contentQueueManager.isQueuePaused()) {
            Timber.d("Content download paused : %s [%s]", content.getTitle(), content.getId());
        }

        if (!contentQueueManager.isQueuePaused()) {
            // Download next content in a new Intent
            Intent intentService = new Intent(Intent.ACTION_SYNC, null, this, ContentDownloadService.class);
            startService(intentService);
        }
    }

    /**
     * Query source to fetch all image file names and URLs of a given book
     *
     * @param content Book whose pages to retrieve
     * @return List of pages with original URLs and file name
     */
    private static List<ImageFile> fetchImageFiles(Content content) {
        // Use ContentParser to query the source
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

    /**
     * Create an image download request an its handler from a given image URL, file name and destination folder
     * @param img Image to download
     * @param dir Destination folder
     * @return Volley request and its handler
     */
    private InputStreamVolleyRequest buildStringRequest(ImageFile img, File dir) {
        return new InputStreamVolleyRequest(Request.Method.GET, img.getUrl(),
                response -> {
                    try {
                        if (response != null) {
                            saveImage(img.getName(), dir, response.getValue().get("Content-Type"), response.getKey());
                            updateImageStatus(img, true);
                        }
                    } catch (IOException e) {
                        Timber.d("I/O error - Image %s not saved", img.getUrl());
                        e.printStackTrace();
                        updateImageStatus(img, false);
                    }
                }, error -> {
            Timber.d("Download error - Image %s not retrieved", img.getUrl());
            error.printStackTrace();
            updateImageStatus(img, false);
        });
    }

    /**
     * Create the given file in the given destination folder, and write binary data to it
     *
     * @param fileName Name of the file to write
     * @param dir Destination folder
     * @param contentType Content type of the image
     * @param binaryContent Binary content of the image
     * @throws IOException IOException if image cannot be saved at given location
     */
    private static void saveImage(String fileName, File dir, String contentType, byte[] binaryContent) throws IOException {
        File file = new File(dir, fileName + "." + MimeTypes.getExtensionFromMimeType(contentType));

        byte buffer[] = new byte[1024];
        int count;

        try (InputStream input = new ByteArrayInputStream(binaryContent)) {
            try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
            }
        }
    }

    /**
     * Update given image status in DB
     *
     * @param img Image to update
     * @param success True if download is successful; false if download failed
     */
    private void updateImageStatus(ImageFile img, boolean success) {
        img.setStatus(success ? StatusContent.DOWNLOADED : StatusContent.ERROR);
        db.updateImageFileStatus(img);
    }

    /**
     * Notify a download progress event to the app using the event bus
     *
     * @param pagesOK Number of pages downloaded successfully on current book
     * @param pagesKO Number of pages whose download failed on current book
     * @param totalPages Total pages of current book
     */
    private static void notifyProgress(int pagesOK, int pagesKO, int totalPages) {
        Timber.d("UpdateActivity : OK : %s - KO : %s - Total : %s > %s pc.", pagesOK, pagesKO, totalPages, String.valueOf((pagesOK + pagesKO) * 100.0 / totalPages));
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PROGRESS, pagesOK, pagesKO, totalPages));
    }

    /**
     * Notify a download completed event to the app using the event bus
     *
     * @param pagesOK Number of pages downloaded successfully on current book
     * @param pagesKO Number of pages whose download failed on current book
     * @param totalPages Total pages of current book
     */
    private static void notifyComplete(int pagesOK, int pagesKO, int totalPages) {
        Timber.d("CompleteActivity : OK = %s; KO = %s", pagesOK, pagesKO);
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_COMPLETE, pagesOK, pagesKO, totalPages));
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            // Nothing special in case of progress
            // case DownloadEvent.EV_PROGRESS:
            case DownloadEvent.EV_PAUSE:
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                RequestQueueManager.getInstance().cancelQueue();
                ContentQueueManager.getInstance().pauseQueue();
                break;
            // Won't be active to catch that
//          case DownloadEvent.EV_UNPAUSE :
            case DownloadEvent.EV_CANCEL:
                RequestQueueManager.getInstance().cancelQueue();
                downloadCanceled = true;
                break;
            case DownloadEvent.EV_SKIP:
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                RequestQueueManager.getInstance().cancelQueue();
                downloadSkipped = true;
                break;
        }
    }
}
