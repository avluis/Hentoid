package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.util.SparseIntArray;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.threeten.bp.Instant;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.fakku.FakkuDecode;
import me.devsaki.fakku.PageInfo;
import me.devsaki.fakku.PointTranslation;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.notification.download.DownloadErrorNotification;
import me.devsaki.hentoid.notification.download.DownloadProgressNotification;
import me.devsaki.hentoid.notification.download.DownloadSuccessNotification;
import me.devsaki.hentoid.notification.download.DownloadWarningNotification;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.exception.UnsupportedContentException;
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
    protected final CompositeDisposable compositeDisposable = new CompositeDisposable();


    public ContentDownloadService() {
        super(ContentDownloadService.class.getName());
    }

    private void notifyStart() {
        notificationManager = new ServiceNotificationManager(this, 1);
        notificationManager.cancel();
        notificationManager.startForeground(new DownloadProgressNotification(this.getResources().getString(R.string.starting_download), 0, 0));

        warningNotificationManager = new NotificationManager(this, 2);
        warningNotificationManager.cancel();
    }

    // Only called once when processing multiple downloads; will be only called
    // if the entire queue is paused (=service destroyed), then resumed (service re-created)
    @Override
    public void onCreate() {
        super.onCreate();

        notifyStart();

        EventBus.getDefault().register(this);

        db = ObjectBoxDB.getInstance(this);

        Timber.d("Download service created");
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        compositeDisposable.clear();

        if (notificationManager != null) notificationManager.cancel();
        ContentQueueManager.getInstance().setInactive();

        Timber.d("Download service destroyed");

        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Timber.d("New intent processed");
        iterateQueue();
    }

    private void iterateQueue() {
        // Process these here to avoid initializing notifications for downloads that will never start
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.w("Queue is paused. Aborting download.");
            return;
        }

        notifyStart();

        Content content = downloadFirstInQueue();
        if (content != null) watchProgress(content);
        else notificationManager.cancel();
    }

    /**
     * Start the download of the 1st book of the download queue
     * <p>
     * NB : This method is not only called the 1st time the queue is awakened,
     * but also after every book has finished downloading
     *
     * @return 1st book of the download queue; null if no book is available to download
     */
    @Nullable
    private Content downloadFirstInQueue() {
        // Check if queue has been paused
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.w("Queue is paused. Aborting download.");
            return null;
        }

        // Work on first item of queue

        // Check if there is a first item to process
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
            notificationManager.notify(new DownloadErrorNotification());
            return null;
        }

        if (StatusContent.DOWNLOADED == content.getStatus()) {
            Timber.w("Content is already downloaded. Aborting download.");
            db.deleteQueue(0);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0));
            notificationManager.notify(new DownloadErrorNotification(content));
            return null;
        }

        downloadCanceled = false;
        downloadSkipped = false;
        db.deleteErrorRecords(content.getId());

        boolean hasError = false;
        int nbErrors = 0;
        // == PREPARATION PHASE ==
        // Parse images from the site (using image list parser)
        //   - Case 1 : If no image is present => parse all images
        //   - Case 2 : If all images are in ERROR state => re-parse all images
        //   - Case 3 : If some images are in ERROR state and the site has backup URLs
        //     => re-parse images with ERROR state using their order as reference
        List<ImageFile> images = content.getImageFiles();
        if (null == images)
            images = new ArrayList<>();
        else
            images = new ArrayList<>(images); // Safe copy of the original list

        for (ImageFile img : images) if (img.getStatus().equals(StatusContent.ERROR)) nbErrors++;

        if (images.isEmpty()
                || nbErrors == images.size()
                || (nbErrors > 0 && content.getSite().hasBackupURLs())
        ) {
            try {
                List<ImageFile> newImages = fetchImageURLs(content);
                // Cases 1 and 2 : Replace existing images with the parsed images
                if (images.isEmpty() || nbErrors == images.size()) images = newImages;
                // Case 3 : Replace images in ERROR state with the parsed images at the same position
                if (nbErrors > 0 && content.getSite().hasBackupURLs()) {
                    for (int i = 0; i < images.size(); i++) {
                        ImageFile oldImage = images.get(i);
                        if (oldImage.getStatus().equals(StatusContent.ERROR)) {
                            for (ImageFile newImg : newImages)
                                if (newImg.getOrder().equals(oldImage.getOrder()))
                                    images.set(i, newImg);
                        }
                    }
                }

                content.setImageFiles(images);
                db.insertContent(content);
            } catch (UnsupportedOperationException uoe) {
                Timber.w(uoe, "A captcha has been found while parsing %s. Aborting download.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.CAPTCHA, content.getUrl(), "Image list", uoe.getMessage());
                hasError = true;
            } catch (LimitReachedException lre) {
                Timber.w(lre, "The bandwidth limit has been reached while parsing %s. Aborting download.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.SITE_LIMIT, content.getUrl(), "Image list", lre.getMessage());
                hasError = true;
            } catch (PreparationInterruptedException ie) {
                Timber.i(ie, "Preparation of %s interrupted", content.getTitle());
                // not an error
            } catch (EmptyResultException ere) {
                Timber.w(ere, "No images have been found while parsing %s. Aborting download.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), "Image list", "No images have been found");
                hasError = true;
            } catch (Exception e) {
                Timber.w(e, "An exception has occurred while parsing %s. Aborting download.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), "Image list", e.getMessage());
                hasError = true;
            }
        } else if (nbErrors > 0) {
            // Other cases : Reset ERROR status of images to mark them as "to be downloaded" (in DB and in memory)
            for (ImageFile img : images) {
                if (img.getStatus().equals(StatusContent.ERROR)) {
                    img.setStatus(StatusContent.SAVED); // SAVED = "to be downloaded"
                    db.updateImageFileStatusAndParams(img);
                }
            }
        }

        if (hasError) {
            content.setStatus(StatusContent.ERROR);
            content.setDownloadDate(Instant.now().toEpochMilli()); // Needs a download date to appear the right location when sorted by download date
            db.insertContent(content);
            db.deleteQueue(content);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0));
            HentoidApp.trackDownloadEvent("Error");
            notificationManager.notify(new DownloadErrorNotification(content));
            return null;
        }

        // In case the download has been canceled while in preparation phase
        // NB : No log of any sort because this is normal behaviour
        if (downloadCanceled || downloadSkipped) return null;

        requestQueueManager = RequestQueueManager.getInstance(this, content.getSite().isAllowParallelDownloads());

        // Create destination folder for images to be downloaded
        DocumentFile dir = ContentHelper.createContentDownloadDir(this, content);
        // Folder creation failed
        if (null == dir || !dir.exists()) {
            String title = content.getTitle();
            String absolutePath = (null == dir) ? "" : dir.getUri().toString();

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

        // Folder creation succeeds -> memorize its path
        content.setStorageUri(dir.getUri().toString());
        if (0 == content.getQtyPages()) content.setQtyPages(images.size());
        content.setStatus(StatusContent.DOWNLOADING);
        db.insertContent(content);

        HentoidApp.trackDownloadEvent("Added");
        Timber.i("Downloading '%s' [%s]", content.getTitle(), content.getId());

        // == DOWNLOAD PHASE ==

        // Queue image download requests
        Site site = content.getSite();
        for (ImageFile img : images) {
            if (img.isCover()) img.setDownloadParams(content.getDownloadParams());
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
            if (null == images) images = Collections.emptyList();
            int nbImages = images.size();

            boolean hasError = false;
            // Less pages than initially detected - More than 10% difference in number of pages
            if (content.getQtyPages() > 0 && nbImages < content.getQtyPages() && Math.abs(nbImages - content.getQtyPages()) > content.getQtyPages() * 0.1) {
                String errorMsg = String.format("The number of images found (%s) does not match the book's number of pages (%s)", nbImages, content.getQtyPages());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getGalleryUrl(), "pages", errorMsg);
                hasError = true;
            }

            DocumentFile dir = DocumentFile.fromTreeUri(this, Uri.parse(content.getStorageUri()));
            if (dir != null && dir.exists()) {
                double freeSpaceRatio = new FileHelper.MemoryUsageFiguresSaf(this, dir).getFreeUsageRatio100();

                // Auto-retry when error pages are remaining and conditions are met
                // NB : Differences between expected and detected pages (see block above) can't be solved by retrying - it's a parsing issue
                if (pagesKO > 0 && Preferences.isDlRetriesActive()
                        && content.getNumberDownloadRetries() < Preferences.getDlRetriesNumber()
                        && (freeSpaceRatio < Preferences.getDlRetriesMemLimit())
                        && requestQueueManager != null
                ) {
                    Timber.i("Auto-retry #%s for content %s (%s%% free space)", content.getNumberDownloadRetries(), content.getTitle(), freeSpaceRatio);
                    logErrorRecord(content.getId(), ErrorType.UNDEFINED, "", content.getTitle(), "Auto-retry #" + content.getNumberDownloadRetries());
                    content.increaseNumberDownloadRetries();

                    // Re-queue all failed images
                    for (ImageFile img : images)
                        if (img.getStatus().equals(StatusContent.ERROR)) {
                            Timber.i("Auto-retry #%s for content %s / image @ %s", content.getNumberDownloadRetries(), content.getTitle(), img.getUrl());
                            img.setStatus(StatusContent.SAVED);
                            db.insertImageFile(img);
                            requestQueueManager.queueRequest(buildDownloadRequest(img, dir, content.getSite().canKnowHentoidAgent(), content.getSite().hasImageProcessing()));
                        }
                    return;
                }
            }

            // Mark content as downloaded
            if (0 == content.getDownloadDate())
                content.setDownloadDate(Instant.now().toEpochMilli());
            content.setStatus((0 == pagesKO && !hasError) ? StatusContent.DOWNLOADED : StatusContent.ERROR);
            // Clear download params from content
            if (0 == pagesKO && !hasError) content.setDownloadParams("");

            db.insertContent(content);

            // Save JSON file
            if (dir != null) {
                if (dir.exists()) {
                    try {
                        DocumentFile jsonFile = JsonHelper.createJson(JsonContent.fromEntity(content), JsonContent.class, dir);
                        // Cache its URI to the newly created content
                        if (jsonFile != null) {
                            content.setJsonUri(jsonFile.getUri().toString());
                            db.insertContent(content);
                        } else {
                            Timber.w("JSON file could not be cached for %s", content.getTitle());
                        }
                    } catch (IOException e) {
                        Timber.e(e, "I/O Error saving JSON: %s", content.getTitle());
                    }
                } else {
                    Timber.w("completeDownload : Directory %s does not exist - JSON not saved", dir.getUri());
                }
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

        // Download next content
        iterateQueue();
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

        if (imgs.isEmpty()) throw new EmptyResultException();

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
            @NonNull ImageFile img,
            @NonNull DocumentFile dir,
            boolean canKnowHentoidAgent,
            boolean hasImageProcessing) {

        String backupUrl = "";

        Map<String, String> headers = new HashMap<>();
        String downloadParamsStr = img.getDownloadParams();
        if (downloadParamsStr != null && downloadParamsStr.length() > 2) // Avoid empty and "{}"
        {
            Map<String, String> downloadParams = null;
            try {
                downloadParams = JsonHelper.jsonToObject(downloadParamsStr, JsonHelper.MAP_STRINGS);
            } catch (IOException e) {
                Timber.w(e);
            }

            if (downloadParams != null) {
                if (downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY))
                    headers.put(HttpHelper.HEADER_COOKIE_KEY, downloadParams.get(HttpHelper.HEADER_COOKIE_KEY));
                if (downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY))
                    headers.put(HttpHelper.HEADER_REFERER_KEY, downloadParams.get(HttpHelper.HEADER_REFERER_KEY));
                if (downloadParams.containsKey("backupUrl"))
                    backupUrl = downloadParams.get("backupUrl");
            }
        }
        final String backupUrlFinal = (null == backupUrl) ? "" : backupUrl;

        return new InputStreamVolleyRequest(
                Request.Method.GET,
                img.getUrl(),
                headers,
                canKnowHentoidAgent,
                result -> onRequestSuccess(result, img, dir, hasImageProcessing, backupUrlFinal),
                error -> onRequestError(error, img, dir, backupUrlFinal));
    }

    private void onRequestSuccess(Map.Entry<byte[], Map<String, String>> result, @NonNull ImageFile img, @NonNull DocumentFile dir, boolean hasImageProcessing, @NonNull String backupUrl) {
        try {
            if (result != null) {
                DocumentFile imgFile = processAndSaveImage(img, dir, result.getValue().get(HttpHelper.HEADER_CONTENT_TYPE), result.getKey(), hasImageProcessing);
                if (imgFile != null)
                    updateImageStatusAndUri(img, true, imgFile.getUri().toString());
            } else {
                updateImageStatusAndUri(img, false, "");
                logErrorRecord(img.content.getTargetId(), ErrorType.UNDEFINED, img.getUrl(), img.getName(), "Result null");
            }
        } catch (UnsupportedContentException e) {
            Timber.w(e);
            if (!backupUrl.isEmpty()) tryUsingBackupUrl(img, dir, backupUrl);
            else {
                Timber.w("No backup URL found - aborting this image");
                updateImageStatusAndUri(img, false, "");
                logErrorRecord(img.content.getTargetId(), ErrorType.UNDEFINED, img.getUrl(), img.getName(), e.getMessage());
            }
        } catch (InvalidParameterException e) {
            Timber.w(e, "Processing error - Image %s not processed properly", img.getUrl());
            updateImageStatusAndUri(img, false, "");
            logErrorRecord(img.content.getTargetId(), ErrorType.IMG_PROCESSING, img.getUrl(), img.getName(), "Download params : " + img.getDownloadParams());
        } catch (IOException e) {
            Timber.w(e, "I/O error - Image %s not saved in dir %s", img.getUrl(), dir.getUri());
            updateImageStatusAndUri(img, false, "");
            logErrorRecord(img.content.getTargetId(), ErrorType.IO, img.getUrl(), img.getName(), "Save failed in dir " + dir.getUri() + " " + e.getMessage());
        }
    }

    private void onRequestError(VolleyError error, @NonNull ImageFile img, @NonNull DocumentFile dir, @NonNull String backupUrl) {
        // Try with the backup URL, if it exists
        if (!backupUrl.isEmpty()) {
            tryUsingBackupUrl(img, dir, backupUrl);
            return;
        }

        // If no backup, then process the error
        String statusCode = (error.networkResponse != null) ? error.networkResponse.statusCode + "" : "N/A";
        String message = error.getMessage() + (img.isBackup() ? " (from backup URL)" : "");
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

        updateImageStatusAndUri(img, false, "");
        logErrorRecord(img.content.getTargetId(), ErrorType.NETWORKING, img.getUrl(), img.getName(), cause + "; HTTP statusCode=" + statusCode + "; message=" + message);
    }

    private void tryUsingBackupUrl(@NonNull ImageFile img, @NonNull DocumentFile dir, @NonNull String backupUrl) {
        Timber.i("Using backup URL %s", backupUrl);
        Content content = img.content.getTarget();
        Site site = content.getSite();
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(site);

        // per Volley behaviour, this method is called on the UI thread
        // -> need to create a new thread to do a network call
        compositeDisposable.add(
                Single.fromCallable(() -> parser.parseBackupUrl(backupUrl, img.getOrder(), content.getQtyPages()))
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                imageFile -> processBackupImage(imageFile, img, dir, site),
                                throwable ->
                                {
                                    updateImageStatusAndUri(img, false, "");
                                    logErrorRecord(img.content.getTargetId(), ErrorType.NETWORKING, img.getUrl(), img.getName(), "Cannot process backup image : message=" + throwable.getMessage());
                                    Timber.e(throwable, "Error processing backup image.");
                                }
                        )
        );
    }

    private void processBackupImage(ImageFile backupImage, @NonNull ImageFile originalImage, @NonNull DocumentFile dir, Site site) {
        if (backupImage != null) {
            Timber.i("Backup URL contains image @ %s; queuing", backupImage.getUrl());
            originalImage.setUrl(backupImage.getUrl()); // Replace original image URL by backup image URL
            originalImage.setBackup(true); // Indicates the image is from a backup (for display in error logs)
            db.insertImageFile(originalImage);
            requestQueueManager.queueRequest(buildDownloadRequest(originalImage, dir, site.canKnowHentoidAgent(), site.hasImageProcessing()));
        } else Timber.w("Failed to parse backup URL");
    }

    private static byte[] processImage(String downloadParamsStr, byte[] binaryContent) throws IOException {
        Map<String, String> downloadParams = JsonHelper.jsonToObject(downloadParamsStr, JsonHelper.MAP_STRINGS);

        if (!downloadParams.containsKey("pageInfo"))
            throw new InvalidParameterException("No pageInfo");

        String pageInfoValue = downloadParams.get("pageInfo");
        if (null == pageInfoValue) throw new InvalidParameterException("PageInfo is null");

        if (pageInfoValue.equals("unprotected"))
            return binaryContent; // Free content, picture is not protected

//        byte[] imgData = Base64.decode(binaryContent, Base64.DEFAULT);
        Bitmap sourcePicture = BitmapFactory.decodeByteArray(binaryContent, 0, binaryContent.length);

        PageInfo page = JsonHelper.jsonToObject(pageInfoValue, PageInfo.class);
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
     * @param contentType   Content type of the image (because some sources don't serve images with extensions)
     * @param binaryContent Binary content of the image
     * @throws IOException IOException if image cannot be saved at given location
     */
    @Nullable
    private static DocumentFile processAndSaveImage(ImageFile img,
                                                    DocumentFile dir,
                                                    String contentType,
                                                    byte[] binaryContent,
                                                    boolean hasImageProcessing) throws IOException, UnsupportedContentException {

        if (!dir.exists()) {
            Timber.w("processAndSaveImage : Directory %s does not exist - image not saved", dir.getUri().toString());
            return null;
        }

        byte[] finalBinaryContent = null;
        if (hasImageProcessing && !img.getName().equals("thumb")) {
            if (img.getDownloadParams() != null && !img.getDownloadParams().isEmpty())
                finalBinaryContent = processImage(img.getDownloadParams(), binaryContent);
            else throw new InvalidParameterException("No processing parameters found");
        }

        String fileExt = null;
        // Determine the extension of the file

        // Use the Content-type contained in the HTTP headers of the response
        if (null != contentType) {
            contentType = HttpHelper.cleanContentType(contentType).first;
            // Ignore neutral binary content-type
            if (!contentType.equalsIgnoreCase("application/octet-stream")) {
                fileExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
                Timber.d("Using content-type %s to determine file extension -> %s", contentType, fileExt);
            }
        }
        // Content-type has not been useful to determine the extension => See if the URL contains an extension
        if (null == fileExt || fileExt.isEmpty()) {
            fileExt = HttpHelper.getExtensionFromUri(img.getUrl());
            Timber.d("Using url to determine file extension (content-type was %s) for %s -> %s", contentType, img.getUrl(), fileExt);
        }
        // No extension detected in the URL => Read binary header of the file to detect known formats
        if (fileExt.isEmpty()) {
            fileExt = FileHelper.getImageExtensionFromPictureHeader(Arrays.copyOf(binaryContent, 12));
            Timber.d("Reading headers to determine file extension for %s -> %s", img.getUrl(), fileExt);
        }
        // If all else fails, fall back to jpg as default
        if (fileExt.isEmpty()) {
            fileExt = "jpg";
            Timber.d("Using default extension for %s -> %s", img.getUrl(), fileExt);
        }

        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
        if (null == mimeType) mimeType = "image/*";

        if (!Helper.isImageExtensionSupported(fileExt))
            throw new UnsupportedContentException(String.format("Unsupported extension %s for %s - image not processed", fileExt, img.getUrl()));
        else
            return saveImage(dir, img.getName() + "." + fileExt, mimeType, (null == finalBinaryContent) ? binaryContent : finalBinaryContent);
    }

    /**
     * Create the given file in the given destination folder, and write binary data to it
     *
     * @param dir           Destination folder
     * @param fileName      Name of the file to write (with the extension)
     * @param binaryContent Binary content of the image
     * @throws IOException IOException if image cannot be saved at given location
     */
    private static DocumentFile saveImage(@NonNull DocumentFile dir, @NonNull String fileName, @NonNull String mimeType, byte[] binaryContent) throws IOException {
        DocumentFile file = dir.createFile(mimeType, fileName);
        FileHelper.saveBinaryInFile(file, binaryContent);
        return file;
    }

    /**
     * Update given image status in DB
     *
     * @param img     Image to update
     * @param success True if download is successful; false if download failed
     */
    private void updateImageStatusAndUri(@NonNull ImageFile img, boolean success, @NonNull String uriStr) {
        img.setStatus(success ? StatusContent.DOWNLOADED : StatusContent.ERROR);
        img.setFileUri(uriStr);
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
                if (requestQueueManager != null) requestQueueManager.cancelQueue();
                ContentQueueManager.getInstance().pauseQueue();
                notificationManager.cancel();
                break;
            case DownloadEvent.EV_CANCEL:
                if (requestQueueManager != null) requestQueueManager.cancelQueue();
                downloadCanceled = true;
                // Tracking Event (Download Canceled)
                HentoidApp.trackDownloadEvent("Cancelled");
                break;
            case DownloadEvent.EV_SKIP:
                db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                if (requestQueueManager != null) requestQueueManager.cancelQueue();
                downloadSkipped = true;
                // Tracking Event (Download Skipped)
                HentoidApp.trackDownloadEvent("Skipped");
                break;
            default:
                // Other events aren't handled here
        }
    }

    private void logErrorRecord(long contentId, ErrorType type, String url, String contentPart, String description) {
        ErrorRecord record = new ErrorRecord(contentId, type, url, contentPart, description, Instant.now());
        if (contentId > 0) db.insertErrorRecord(record);
    }
}
