package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.SparseIntArray;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import me.devsaki.fakku.FakkuDecode;
import me.devsaki.fakku.PageInfo;
import me.devsaki.fakku.PointTranslation;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.notification.download.DownloadErrorNotification;
import me.devsaki.hentoid.notification.download.DownloadProgressNotification;
import me.devsaki.hentoid.notification.download.DownloadSuccessNotification;
import me.devsaki.hentoid.notification.download.DownloadWarningNotification;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.ImageListParser;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.NotificationManager;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;


/**
 * Created by Robb_w on 2018/04
 * Book download service; 1 instance everytime a new book of the queue has to be downloaded
 * NB : As per IntentService behaviour, only one thread can be active at a time (no parallel runs of ContentDownloadService)
 */
public class ContentDownloadService extends IntentService {

    private ObjectBoxDB db;                                   // Hentoid database
    private ServiceNotificationManager notificationManager;
    private NotificationManager warningNotificationManager;
    private boolean downloadCanceled;                       // True if a Cancel event has been processed; false by default
    private boolean downloadSkipped;                        // True if a Skip event has been processed; false by default

    private RequestQueueManager<Object> requestQueueManager = null;

    public ContentDownloadService() {
        super(ContentDownloadService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = new ServiceNotificationManager(this, 1);
        notificationManager.cancel();
        notificationManager.startForeground(new DownloadProgressNotification("Starting download", 0, 0));

        warningNotificationManager = new NotificationManager(this, 2);
        warningNotificationManager.cancel();

        EventBus.getDefault().register(this);

        db = ObjectBoxDB.getInstance(this);

        Timber.d("Download service created");
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);

        Timber.d("Download service destroyed");
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Timber.d("New intent processed");

        Content content = downloadFirstInQueue();
        if (content != null) watchProgress(content);
    }

    /**
     * Start the download of the 1st book of the download queue
     *
     * @return 1st book of the download queue
     */
    @Nullable
    private Content downloadFirstInQueue() {
        // Check if queue is already paused
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.w("Queue is paused. Aborting download.");
            return null;
        }

        // Works on first item of queue
        List<QueueRecord> queue = db.selectQueue();
        if (queue.isEmpty()) {
            Timber.w("Queue is empty. Aborting download.");
            return null;
        }

        Content content = queue.get(0).content.getTarget();

        if (null == content) {
            Timber.w("Content is unavailable. Aborting download.");
            db.deleteQueue(0);
            content = new Content().setId(queue.get(0).content.getTargetId()); // Must supply content ID to the event for the UI to update properly
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0));
            return null;
        }

        if (StatusContent.DOWNLOADED == content.getStatus()) {
            Timber.w("Content is already downloaded. Aborting download.");
            db.deleteQueue(0);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0));
            return null;
        }

        downloadCanceled = false;
        downloadSkipped = false;
        db.deleteErrorRecords(content.getId());

        boolean hasError = false;
        // Check if images are already known
        List<ImageFile> images = content.getImageFiles();
        if (null == images || images.isEmpty()) {
            try {
                images = fetchImageURLs(content);
                content.addImageFiles(images);
                db.insertContent(content);
            } catch (UnsupportedOperationException u) {
                Timber.w(u, "A captcha has been found while parsing %s. Aborting download.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.CAPTCHA, content.getUrl(), "Image list", u.getMessage());
                hasError = true;
            } catch (Exception e) {
                Timber.w(e, "An exception has occurred while parsing %s. Aborting download.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), "Image list", e.getMessage());
                hasError = true;
            }
        }

        if (hasError) {
            content.setStatus(StatusContent.ERROR);
            db.insertContent(content);
            db.deleteQueue(content);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0));
            HentoidApp.trackDownloadEvent("Error");
            return null;
        }

        // Could have been canceled while preparing the download
        if (downloadCanceled || downloadSkipped) return null;

        File dir = FileHelper.createContentDownloadDir(this, content);
        if (!dir.exists()) {
            String title = content.getTitle();
            String absolutePath = dir.getAbsolutePath();

            // Log everywhere
            String message = String.format("Directory could not be created: %s.", absolutePath);
            Timber.w(message);
            logErrorRecord(content.getId(), ErrorType.IO, content.getUrl(), "Destination folder", message);
            warningNotificationManager.notify(new DownloadWarningNotification(title, absolutePath));

            // No sense in waiting for every image to be downloaded in error state (terrible waste of network resources)
            // => Create all images, flag them as failed as well as the book
            for (ImageFile img : images) {
                if (img.getStatus().equals(StatusContent.SAVED)) {
                    img.setStatus(StatusContent.ERROR);
                    db.updateImageFileStatusAndParams(img);
                }
            }
            completeDownload(content, 0, images.size());
            return null;
        }

        String fileRoot = Preferences.getRootFolderName();
        content.setStorageFolder(dir.getAbsolutePath().substring(fileRoot.length()));
        content.setStatus(StatusContent.DOWNLOADING);
        db.insertContent(content);


        // Tracking Event (Download Added)
        HentoidApp.trackDownloadEvent("Added");

        Timber.i("Downloading '%s' [%s]", content.getTitle(), content.getId());

        // Reset ERROR status of images to count them as "to be downloaded" (in DB and in memory)
        for (ImageFile img : images) {
            if (img.getStatus().equals(StatusContent.ERROR)) {
                img.setStatus(StatusContent.SAVED);
                db.updateImageFileStatusAndParams(img);
            }
        }

        // Queue image download requests
        ImageFile cover = new ImageFile().setName("thumb").setUrl(content.getCoverImageUrl());
        Site site = content.getSite();
        requestQueueManager = RequestQueueManager.getInstance(this, site.isAllowParallelDownloads());
        requestQueueManager.queueRequest(buildDownloadRequest(cover, dir, site.canKnowHentoidAgent(), site.hasImageProcessing()));
        for (ImageFile img : images) {
            if (img.getStatus().equals(StatusContent.SAVED))
                requestQueueManager.queueRequest(buildDownloadRequest(img, dir, site.canKnowHentoidAgent(), site.hasImageProcessing()));
        }

        return content;
    }

    /**
     * Watch download progress
     * <p>
     * NB : download pause is managed at the Volley queue level (see RequestQueueManager.pauseQueue / startQueue)
     *
     * @param content Content to watch (1st book of the download queue)
     */
    private void watchProgress(Content content) {
        boolean isDone;
        int pagesOK;
        int pagesKO;
        List<ImageFile> images = content.getImageFiles();
        ContentQueueManager contentQueueManager = ContentQueueManager.getInstance();

        do {
            SparseIntArray statuses = db.countProcessedImagesById(content.getId());
            pagesOK = statuses.get(StatusContent.DOWNLOADED.getCode());
            pagesKO = statuses.get(StatusContent.ERROR.getCode());

            String title = content.getTitle();
            int totalPages = (null == images) ? 0 : images.size();
            int progress = pagesOK + pagesKO;
            isDone = progress == totalPages;
            Timber.d("Progress: OK:%s KO:%s Total:%s", pagesOK, pagesKO, totalPages);
            notificationManager.notify(new DownloadProgressNotification(title, progress, totalPages));
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_PROGRESS, pagesOK, pagesKO, totalPages));

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        while (!isDone && !downloadCanceled && !downloadSkipped && !contentQueueManager.isQueuePaused()); // TODO - Observe DB instead ?

        if (contentQueueManager.isQueuePaused()) {
            Timber.d("Content download paused : %s [%s]", content.getTitle(), content.getId());
            if (downloadCanceled) notificationManager.cancel();
        } else {
            completeDownload(content, pagesOK, pagesKO);
        }
    }

    /**
     * Completes the download of a book when all images have been processed
     * Then launches a new IntentService
     *
     * @param content Content to mark as downloaded
     */
    private void completeDownload(Content content, int pagesOK, int pagesKO) {
        ContentQueueManager contentQueueManager = ContentQueueManager.getInstance();

        if (!downloadCanceled && !downloadSkipped) {
            List<ImageFile> images = content.getImageFiles();
            int nbImages = (null == images) ? 0 : images.size();

            boolean hasError = false;
            // Less pages than initially detected - More than 10% difference in number of pages
            if (nbImages < content.getQtyPages() && Math.abs(nbImages - content.getQtyPages()) > content.getQtyPages() * 0.1) {
                String errorMsg = String.format("The number of images found (%s) does not match the book's number of pages (%s)", nbImages, content.getQtyPages());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getGalleryUrl(), "pages", errorMsg);
                hasError = true;
            }

            // Mark content as downloaded
            content.setDownloadDate(new Date().getTime());
            content.setStatus((0 == pagesKO && !hasError) ? StatusContent.DOWNLOADED : StatusContent.ERROR);
            // Clear download params from content
            if (0 == pagesKO && !hasError) content.setDownloadParams("");

            db.insertContent(content);

            // Save JSON file
            File dir = FileHelper.createContentDownloadDir(this, content);
            if (dir.exists()) {
                try {
                    File jsonFile = JsonHelper.createJson(content.preJSONExport(), dir);
                    // Cache its URI to the newly created content
                    DocumentFile jsonDocFile = FileHelper.getDocumentFile(jsonFile, false);
                    if (jsonDocFile != null) {
                        content.setJsonUri(jsonDocFile.getUri().toString());
                        db.insertContent(content);
                    } else {
                        Timber.w("JSON file could not be cached for %s", content.getTitle());
                    }
                } catch (IOException e) {
                    Timber.e(e, "I/O Error saving JSON: %s", content.getTitle());
                }
            } else {
                Timber.w("completeDownload : Directory %s does not exist - JSON not saved", dir.getAbsolutePath());
            }

            Timber.i("Content download finished: %s [%s]", content.getTitle(), content.getId());

            // Delete book from queue
            db.deleteQueue(content);

            // Increase downloads count
            contentQueueManager.downloadComplete();

            if (0 == pagesKO) {
                int downloadCount = contentQueueManager.getDownloadCount();
                notificationManager.notify(new DownloadSuccessNotification(downloadCount));

                // Tracking Event (Download Success)
                HentoidApp.trackDownloadEvent("Success");
            } else {
                notificationManager.notify(new DownloadErrorNotification(content));

                // Tracking Event (Download Error)
                HentoidApp.trackDownloadEvent("Error");
            }

            // Signals current download as completed
            Timber.d("CompleteActivity : OK = %s; KO = %s", pagesOK, pagesKO);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, pagesOK, pagesKO, nbImages));

            // Tracking Event (Download Completed)
            HentoidApp.trackDownloadEvent("Completed");
        } else if (downloadCanceled) {
            Timber.d("Content download canceled: %s [%s]", content.getTitle(), content.getId());
            notificationManager.cancel();
        } else {
            Timber.d("Content download skipped : %s [%s]", content.getTitle(), content.getId());
        }

        // Download next content in a new Intent
        contentQueueManager.resumeQueue(this);
    }

    /**
     * Query source to fetch all image file names and URLs of a given book
     *
     * @param content Book whose pages to retrieve
     * @return List of pages with original URLs and file name
     */
    private List<ImageFile> fetchImageURLs(Content content) throws Exception {
        List<ImageFile> imgs;
        // Use ImageListParser to query the source
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(content);
        imgs = parser.parseImageList(content);

        if (imgs.isEmpty())
            throw new Exception("An empty image list has been found while parsing " + content.getGalleryUrl());

        for (ImageFile img : imgs) img.setStatus(StatusContent.SAVED);
        return imgs;
    }

    /**
     * Create an image download request an its handler from a given image URL, file name and destination folder
     *
     * @param img Image to download
     * @param dir Destination folder
     * @return Volley request and its handler
     */
    private Request<Object> buildDownloadRequest(
            ImageFile img,
            File dir,
            boolean canKnowHentoidAgent,
            boolean hasImageProcessing) {

        Map<String, String> headers = new HashMap<>();
        String downloadParamsStr = img.getDownloadParams();
        if (downloadParamsStr != null && !downloadParamsStr.isEmpty()) {
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> downloadParams = new Gson().fromJson(downloadParamsStr, type);

            if (downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY))
                headers.put(HttpHelper.HEADER_COOKIE_KEY, downloadParams.get(HttpHelper.HEADER_COOKIE_KEY));
            if (downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY))
                headers.put(HttpHelper.HEADER_REFERER_KEY, downloadParams.get(HttpHelper.HEADER_REFERER_KEY));
        }

        return new InputStreamVolleyRequest(
                Request.Method.GET,
                img.getUrl(),
                headers,
                canKnowHentoidAgent,
                result -> onRequestSuccess(result, img, dir, hasImageProcessing),
                error -> onRequestError(error, img));
    }

    private void onRequestSuccess(Map.Entry<byte[], Map<String, String>> result, ImageFile img, File dir, boolean hasImageProcessing) {
        try {
            if (result != null) {
                processAndSaveImage(img, dir, result.getValue().get("Content-Type"), result.getKey(), hasImageProcessing);
                updateImageStatus(img, true);
            } else {
                updateImageStatus(img, false);
                logErrorRecord(img.content.getTargetId(), ErrorType.UNDEFINED, img.getUrl(), img.getName(), "Result null");
            }
        } catch (InvalidParameterException e) {
            Timber.w(e, "Processing error - Image %s not processed properly", img.getUrl());
            updateImageStatus(img, false);
            logErrorRecord(img.content.getTargetId(), ErrorType.IMG_PROCESSING, img.getUrl(), img.getName(), "Download params : " + img.getDownloadParams());
        } catch (IOException e) {
            Timber.w(e, "I/O error - Image %s not saved in dir %s", img.getUrl(), dir.getPath());
            updateImageStatus(img, false);
            logErrorRecord(img.content.getTargetId(), ErrorType.IO, img.getUrl(), img.getName(), "Save failed in dir " + dir.getAbsolutePath());
        }
    }

    private void onRequestError(VolleyError error, ImageFile img) {
        String statusCode = (error.networkResponse != null) ? error.networkResponse.statusCode + "" : "N/A";
        String message = error.getMessage();
        String cause = "";

        if (error instanceof TimeoutError) {
            cause = "Timeout";
        } else if (error instanceof NoConnectionError) {
            cause = "No connection";
        } else if (error instanceof AuthFailureError) {
            cause = "Auth failure";
        } else if (error instanceof ServerError) {
            cause = "Server error";
        } else if (error instanceof NetworkError) {
            cause = "Network error";
        } else if (error instanceof ParseError) {
            cause = "Network parse error";
        }

        Timber.w(error);

        updateImageStatus(img, false);
        logErrorRecord(img.content.getTargetId(), ErrorType.NETWORKING, img.getUrl(), img.getName(), cause + "; HTTP statusCode=" + statusCode + "; message=" + message);
    }

    private static byte[] processImage(String downloadParamsStr, byte[] binaryContent) throws InvalidParameterException {
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();

        Map<String, String> downloadParams = new Gson().fromJson(downloadParamsStr, type);
        if (!downloadParams.containsKey("pageInfo"))
            throw new InvalidParameterException("No pageInfo");

        String pageInfoValue = downloadParams.get("pageInfo");
        if (null == pageInfoValue) throw new InvalidParameterException("PageInfo is null");

        if (pageInfoValue.equals("unprotected"))
            return binaryContent; // Free content, picture is not protected

//        byte[] imgData = Base64.decode(binaryContent, Base64.DEFAULT);
        Bitmap sourcePicture = BitmapFactory.decodeByteArray(binaryContent, 0, binaryContent.length);

        PageInfo page = new Gson().fromJson(pageInfoValue, PageInfo.class);
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap destPicture = Bitmap.createBitmap(page.width, page.height, conf);
        Canvas destCanvas = new Canvas(destPicture);

        FakkuDecode.getTranslations(page);

        if (page.translations.isEmpty())
            throw new InvalidParameterException("No translation found");

        for (PointTranslation t : page.translations) {
            Rect sourceRect = new Rect(t.sourceX, t.sourceY, t.sourceX + FakkuDecode.TILE_EDGE_LENGTH, t.sourceY + FakkuDecode.TILE_EDGE_LENGTH);
            Rect destRect = new Rect(t.destX, t.destY, t.destX + FakkuDecode.TILE_EDGE_LENGTH, t.destY + FakkuDecode.TILE_EDGE_LENGTH);

            destCanvas.drawBitmap(sourcePicture, sourceRect, destRect, null);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        destPicture.compress(Bitmap.CompressFormat.PNG, 100, out); // Fakku is _always_ PNG
        return out.toByteArray();
    }

    /**
     * Create the given file in the given destination folder, and write binary data to it
     *
     * @param img           ImageFile that is being processed
     * @param dir           Destination folder
     * @param contentType   Content type of the image
     * @param binaryContent Binary content of the image
     * @throws IOException IOException if image cannot be saved at given location
     */
    private static void processAndSaveImage(ImageFile img, File dir, String contentType, byte[] binaryContent, boolean hasImageProcessing) throws IOException, InvalidParameterException {

        if (!dir.exists()) {
            Timber.w("processAndSaveImage : Directory %s does not exist - image not saved", dir.getAbsolutePath());
            return;
        }

        byte[] finalBinaryContent = null;
        if (hasImageProcessing && !img.getName().equals("thumb")) {
            if (img.getDownloadParams() != null && !img.getDownloadParams().isEmpty())
                finalBinaryContent = processImage(img.getDownloadParams(), binaryContent);
            else throw new InvalidParameterException("No processing parameters found");
        }

        saveImage(img.getName(), dir, contentType, (null == finalBinaryContent) ? binaryContent : finalBinaryContent);
    }

    /**
     * Create the given file in the given destination folder, and write binary data to it
     *
     * @param fileName      Name of the file to write
     * @param dir           Destination folder
     * @param contentType   Content type of the image
     * @param binaryContent Binary content of the image
     * @throws IOException IOException if image cannot be saved at given location
     */
    private static void saveImage(String fileName, File dir, String contentType, byte[] binaryContent) throws IOException {
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
        File file = new File(dir, fileName + "." + ext);
        FileHelper.saveBinaryInFile(file, binaryContent);
    }

    /**
     * Update given image status in DB
     *
     * @param img     Image to update
     * @param success True if download is successful; false if download failed
     */
    private void updateImageStatus(ImageFile img, boolean success) {
        img.setStatus(success ? StatusContent.DOWNLOADED : StatusContent.ERROR);
        if (success) img.setDownloadParams("");
        if (img.getId() > 0)
            db.updateImageFileStatusAndParams(img); // because thumb image isn't in the DB
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    public void onDownloadEvent(DownloadEvent event) {
        switch (event.eventType) {
            case DownloadEvent.EV_PAUSE:
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                requestQueueManager.cancelQueue();
                ContentQueueManager.getInstance().pauseQueue();
                notificationManager.cancel();
                break;
            case DownloadEvent.EV_CANCEL:
                requestQueueManager.cancelQueue();
                downloadCanceled = true;
                // Tracking Event (Download Canceled)
                HentoidApp.trackDownloadEvent("Cancelled");
                break;
            case DownloadEvent.EV_SKIP:
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                requestQueueManager.cancelQueue();
                downloadSkipped = true;
                // Tracking Event (Download Skipped)
                HentoidApp.trackDownloadEvent("Skipped");
                break;
            default:
                // Other events aren't handled here
        }
    }

    private void logErrorRecord(long contentId, ErrorType type, String url, String contentPart, String description) {
        ErrorRecord record = new ErrorRecord(contentId, type, url, contentPart, description);
        if (contentId > 0) db.insertErrorRecord(record);
    }
}
