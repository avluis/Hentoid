package me.devsaki.hentoid.workers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.annimon.stream.Stream;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.threeten.bp.Instant;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.fakku.FakkuDecode;
import me.devsaki.fakku.PageInfo;
import me.devsaki.fakku.PointTranslation;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
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
import me.devsaki.hentoid.util.DuplicateHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.download.RequestQueueManager;
import me.devsaki.hentoid.util.exception.AccountException;
import me.devsaki.hentoid.util.exception.CaptchaException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.exception.UnsupportedContentException;
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.network.InputStreamVolleyRequest;
import me.devsaki.hentoid.util.network.NetworkHelper;
import me.devsaki.hentoid.util.notification.Notification;
import timber.log.Timber;

public class ContentDownloadWorker extends BaseWorker {

    private enum QueuingResult {
        CONTENT_FOUND, CONTENT_SKIPPED, CONTENT_FAILED, QUEUE_END
    }

    // DAO is full scope to avoid putting try / finally's everywhere and be sure to clear it upon worker stop
    private final CollectionDAO dao;

    private boolean downloadCanceled;                       // True if a Cancel event has been processed; false by default
    private boolean downloadSkipped;                        // True if a Skip event has been processed; false by default

    private final RequestQueueManager<Object> requestQueueManager;
    protected final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Download speed calculator
    private final DownloadSpeedCalculator downloadSpeedCalculator = new DownloadSpeedCalculator();


    public ContentDownloadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.download_service);

        EventBus.getDefault().register(this);
        dao = new ObjectBoxDAO(context);

        requestQueueManager = RequestQueueManager.getInstance(context);
    }

    @Override
    Notification getStartNotification() {
        String message = getApplicationContext().getResources().getString(R.string.starting_download);
        return new DownloadProgressNotification(message, 0, 0, 0, 0, 0);
    }

    @Override
    void onInterrupt() {
        requestQueueManager.cancelQueue();
        downloadCanceled = true;
    }

    @Override
    void onClear() {
        EventBus.getDefault().unregister(this);
        compositeDisposable.clear();

        if (dao != null) dao.cleanup();

        ContentQueueManager.getInstance().setInactive();
    }

    @Override
    void getToWork(@NonNull Data input) {
        iterateQueue();
    }

    private void iterateQueue() {
        // Process these here to avoid initializing notifications for downloads that will never start
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.w("Queue is paused. Download aborted.");
            return;
        }

        ImmutablePair<QueuingResult, Content> result = downloadFirstInQueue();
        while (!result.left.equals(QueuingResult.QUEUE_END)) {
            if (result.left.equals(QueuingResult.CONTENT_FOUND)) watchProgress(result.right);
            result = downloadFirstInQueue();
        }
        notificationManager.cancel();
    }

    /**
     * Start the download of the 1st book of the download queue
     * <p>
     * NB : This method is not only called the 1st time the queue is awakened,
     * but also after every book has finished downloading
     *
     * @return 1st book of the download queue; null if no book is available to download
     */
    @SuppressLint("TimberExceptionLogging")
    @NonNull
    private ImmutablePair<QueuingResult, Content> downloadFirstInQueue() {
        final String CONTENT_PART_IMAGE_LIST = "Image list";

        Context context = getApplicationContext();

        // Clear previously created requests
        compositeDisposable.clear();

        // Check if queue has been paused
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.w("Queue is paused. Download aborted.");
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        @NetworkHelper.Connectivity int connectivity = NetworkHelper.getConnectivity(context);
        // Check for network connectivity
        if (NetworkHelper.Connectivity.NO_INTERNET == connectivity) {
            Timber.w("No internet connection available. Queue paused.");
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE, DownloadEvent.Motive.NO_INTERNET));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        // Check for wifi if wifi-only mode is on
        if (Preferences.isQueueWifiOnly() && NetworkHelper.Connectivity.WIFI != connectivity) {
            Timber.w("No wi-fi connection available. Queue paused.");
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE, DownloadEvent.Motive.NO_WIFI));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        // Check for download folder existence, available free space and credentials
        if (Preferences.getStorageUri().trim().isEmpty()) {
            Timber.w("No download folder set"); // May happen if user has skipped it during the intro
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE, DownloadEvent.Motive.NO_DOWNLOAD_FOLDER));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
        if (null == rootFolder) {
            Timber.w("Download folder has not been found. Please select it again."); // May happen if the folder has been moved or deleted after it has been selected
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE, DownloadEvent.Motive.DOWNLOAD_FOLDER_NOT_FOUND));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }
        if (-2 == FileHelper.createNoMedia(context, rootFolder)) {
            Timber.w("Insufficient credentials on download folder. Please select it again.");
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE, DownloadEvent.Motive.DOWNLOAD_FOLDER_NO_CREDENTIALS));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }
        if (new FileHelper.MemoryUsageFigures(context, rootFolder).getfreeUsageMb() < 2) {
            Timber.w("Device very low on storage space (<2 MB). Queue paused.");
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE, DownloadEvent.Motive.NO_STORAGE));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        // Work on first item of queue

        // Check if there is a first item to process
        List<QueueRecord> queue = dao.selectQueue();
        if (queue.isEmpty()) {
            Timber.w("Queue is empty. Download aborted.");
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        Content content = queue.get(0).getContent().getTarget();

        if (null == content) {
            Timber.w("Content is unavailable. Download aborted.");
            dao.deleteQueue(0);
            content = new Content().setId(queue.get(0).getContent().getTargetId()); // Must supply content ID to the event for the UI to update properly
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0, 0));
            notificationManager.notify(new DownloadErrorNotification());
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
        }

        if (StatusContent.DOWNLOADED == content.getStatus()) {
            Timber.w("Content is already downloaded. Download aborted.");
            dao.deleteQueue(0);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0, 0));
            notificationManager.notify(new DownloadErrorNotification(content));
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
        }

        downloadCanceled = false;
        downloadSkipped = false;
        dao.deleteErrorRecords(content.getId());

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

                // Manually insert new images (without using insertContent)
                long contentId = content.getId();
                dao.replaceImageList(contentId, images);
                // Get updated Content with the generated ID of new images
                content = dao.selectContent(contentId);
                if (null == content)
                    return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
            } catch (CaptchaException cpe) {
                Timber.w(cpe, "A captcha has been found while parsing %s. Download aborted.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.CAPTCHA, content.getUrl(), CONTENT_PART_IMAGE_LIST, "Captcha found. Please go back to the site, browse a book and solve the captcha.");
                hasError = true;
            } catch (AccountException ae) {
                String description = String.format("Your %s account does not allow to download the book %s. %s. Download aborted.", content.getSite().getDescription(), content.getTitle(), ae.getMessage());
                Timber.w(ae, description);
                logErrorRecord(content.getId(), ErrorType.ACCOUNT, content.getUrl(), CONTENT_PART_IMAGE_LIST, description);
                hasError = true;
            } catch (LimitReachedException lre) {
                String description = String.format("The bandwidth limit has been reached while parsing %s. %s. Download aborted.", content.getTitle(), lre.getMessage());
                Timber.w(lre, description);
                logErrorRecord(content.getId(), ErrorType.SITE_LIMIT, content.getUrl(), CONTENT_PART_IMAGE_LIST, description);
                hasError = true;
            } catch (PreparationInterruptedException ie) {
                Timber.i(ie, "Preparation of %s interrupted", content.getTitle());
                // not an error
            } catch (EmptyResultException ere) {
                Timber.w(ere, "No images have been found while parsing %s. Download aborted.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), CONTENT_PART_IMAGE_LIST, "No images have been found. Error = " + ere.getMessage());
                hasError = true;
            } catch (Exception e) {
                if (null == content)
                    return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
                Timber.w(e, "An exception has occurred while parsing %s. Download aborted.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), CONTENT_PART_IMAGE_LIST, e.getMessage());
                hasError = true;
            }
        } else if (nbErrors > 0) {
            // Other cases : Reset ERROR status of images to mark them as "to be downloaded" (in DB and in memory)
            dao.updateImageContentStatus(content.getId(), StatusContent.ERROR, StatusContent.SAVED);
        }

        if (hasError) {
            moveToErrors(content.getId());
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, 0, 0, 0, 0));
            return new ImmutablePair<>(QueuingResult.CONTENT_FAILED, content);
        }

        // In case the download has been canceled while in preparation phase
        // NB : No log of any sort because this is normal behaviour
        if (downloadCanceled || downloadSkipped)
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);

        // Create destination folder for images to be downloaded
        DocumentFile dir = ContentHelper.getOrCreateContentDownloadDir(getApplicationContext(), content);
        // Folder creation failed
        if (null == dir || !dir.exists()) {
            String title = content.getTitle();
            String absolutePath = (null == dir) ? "" : dir.getUri().toString();

            String message = String.format("Directory could not be created: %s.", absolutePath);
            Timber.w(message);
            logErrorRecord(content.getId(), ErrorType.IO, content.getUrl(), "Destination folder", message);
            notificationManager.notify(new DownloadWarningNotification(title, absolutePath));

            // No sense in waiting for every image to be downloaded in error state (terrible waste of network resources)
            // => Create all images, flag them as failed as well as the book
            dao.updateImageContentStatus(content.getId(), StatusContent.SAVED, StatusContent.ERROR);
            completeDownload(content.getId(), content.getTitle(), 0, images.size(), 0);
            return new ImmutablePair<>(QueuingResult.CONTENT_FAILED, content);
        }

        // Folder creation succeeds -> memorize its path
        content.setStorageUri(dir.getUri().toString());
        // Set QtyPages if the content parser couldn't do it (certain sources only)
        // Don't count the cover thumbnail in the number of pages
        if (0 == content.getQtyPages()) content.setQtyPages(images.size() - 1);
        content.setStatus(StatusContent.DOWNLOADING);
        dao.insertContent(content);

        HentoidApp.trackDownloadEvent("Added");
        Timber.i("Downloading '%s' [%s]", content.getTitle(), content.getId());

        // == DOWNLOAD PHASE ==

        // Queue image download requests
        Site site = content.getSite();
        for (ImageFile img : images) {
            if (img.getStatus().equals(StatusContent.SAVED)) {
                if (img.isCover()) {
                    // Enrich cover download params just in case
                    Map<String, String> downloadParams;
                    if (img.getDownloadParams().length() > 2)
                        downloadParams = ContentHelper.parseDownloadParams(img.getDownloadParams());
                    else
                        downloadParams = new HashMap<>();
                    // Add the referer, if unset
                    if (!downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY))
                        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getGalleryUrl());
                    // Set the 1st image of the list as a backup, if the cover URL is stale (might happen when restarting old downloads)
                    if (images.size() > 1)
                        downloadParams.put("backupUrl", images.get(1).getUrl());
                    img.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));
                }
                requestQueueManager.queueRequest(buildDownloadRequest(img, dir, site));
            }
        }

        if (ContentHelper.updateQueueJson(getApplicationContext(), dao))
            Timber.i("Queue JSON successfully saved");
        else Timber.w("Queue JSON saving failed");

        return new ImmutablePair<>(QueuingResult.CONTENT_FOUND, content);
    }

    /**
     * Watch download progress
     * <p>
     * NB : download pause is managed at the Volley queue level (see RequestQueueManager.pauseQueue / startQueue)
     *
     * @param content Content to watch (1st book of the download queue)
     */
    private void watchProgress(@NonNull Content content) {
        boolean isDone;
        int pagesOK = 0;
        int pagesKO = 0;
        long sizeDownloadedBytes = 0;

        List<ImageFile> images = content.getImageFiles();
        int totalPages = (null == images) ? 0 : images.size();

        ContentQueueManager contentQueueManager = ContentQueueManager.getInstance();
        do {
            Map<StatusContent, ImmutablePair<Integer, Long>> statuses = dao.countProcessedImagesById(content.getId());
            ImmutablePair<Integer, Long> status = statuses.get(StatusContent.DOWNLOADED);
            if (status != null) {
                pagesOK = status.left;
                sizeDownloadedBytes = status.right;
            }
            status = statuses.get(StatusContent.ERROR);
            if (status != null)
                pagesKO = status.left;

            double sizeDownloadedMB = sizeDownloadedBytes / (1024.0 * 1024);
            int progress = pagesOK + pagesKO;
            isDone = progress == totalPages;
            Timber.d("Progress: OK:%d size:%dMB - KO:%d - Total:%d", pagesOK, (int) sizeDownloadedMB, pagesKO, totalPages);

            // Download speed and size estimation
            downloadSpeedCalculator.addSampleNow(NetworkHelper.getIncomingNetworkUsage(getApplicationContext()));
            int avgSpeedKbps = (int) downloadSpeedCalculator.getAvgSpeedKbps();

            double estimateBookSizeMB = -1;
            if (pagesOK > 3 && progress > 0 && totalPages > 0) {
                estimateBookSizeMB = sizeDownloadedMB / (progress * 1.0 / totalPages);
                Timber.d("Estimate book size calculated for wifi check : %s MB", estimateBookSizeMB);
            }

            notificationManager.notify(new DownloadProgressNotification(content.getTitle(), progress, totalPages, (int) sizeDownloadedMB, (int) estimateBookSizeMB, avgSpeedKbps));
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_PROGRESS, pagesOK, pagesKO, totalPages, sizeDownloadedBytes));

            // If the "skip large downloads on mobile data" is on, skip if needed
            if (Preferences.isDownloadLargeOnlyWifi() &&
                    (estimateBookSizeMB > Preferences.getDownloadLargeOnlyWifiThresholdMB()
                            || totalPages > Preferences.getDownloadLargeOnlyWifiThresholdPages()
                    )
            ) {
                @NetworkHelper.Connectivity int connectivity = NetworkHelper.getConnectivity(getApplicationContext());
                if (NetworkHelper.Connectivity.WIFI != connectivity) {
                    // Move the book to the errors queue and signal it as skipped
                    logErrorRecord(content.getId(), ErrorType.WIFI, content.getUrl(), "Book", "");
                    moveToErrors(content.getId());
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
                }
            }

            // We're polling the DB because we can't observe LiveData from a background service
            try {
                //noinspection BusyWait
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Timber.w(e);
                Thread.currentThread().interrupt();
            }
        }
        while (!isDone && !downloadCanceled && !downloadSkipped && !contentQueueManager.isQueuePaused());

        if (contentQueueManager.isQueuePaused()) {
            Timber.d("Content download paused : %s [%s]", content.getTitle(), content.getId());
            if (downloadCanceled) notificationManager.cancel();
        } else {
            // NB : no need to supply the Content itself as it has not been updated during the loop
            completeDownload(content.getId(), content.getTitle(), pagesOK, pagesKO, sizeDownloadedBytes);
        }
    }

    /**
     * Completes the download of a book when all images have been processed
     * Then launches a new IntentService
     *
     * @param contentId Id of the Content to mark as downloaded
     */
    private void completeDownload(final long contentId, @NonNull final String title,
                                  final int pagesOK, final int pagesKO, final long sizeDownloadedBytes) {
        ContentQueueManager contentQueueManager = ContentQueueManager.getInstance();
        // Get the latest value of Content
        Content content = dao.selectContent(contentId);
        if (null == content) {
            Timber.w("Content ID %s not found", contentId);
            return;
        }

        if (!downloadCanceled && !downloadSkipped) {
            List<ImageFile> images = content.getImageFiles();
            if (null == images) images = Collections.emptyList();
            int nbImages = (int) Stream.of(images).filter(i -> !i.isCover()).count(); // Don't count the cover

            boolean hasError = false;
            // Set error state if less pages than initially detected - More than 10% difference in number of pages
            if (content.getQtyPages() > 0 && nbImages < content.getQtyPages() && Math.abs(nbImages - content.getQtyPages()) > content.getQtyPages() * 0.1) {
                String errorMsg = String.format("The number of images found (%s) does not match the book's number of pages (%s)", nbImages, content.getQtyPages());
                logErrorRecord(contentId, ErrorType.PARSING, content.getGalleryUrl(), "pages", errorMsg);
                hasError = true;
            }
            // Set error state if there are non-downloaded pages
            // NB : this should not happen theoretically
            long nbDownloadedPages = content.getNbDownloadedPages();
            if (nbDownloadedPages < content.getQtyPages()) {
                Timber.i(">> downloaded vs. qty KO %s vs %s", nbDownloadedPages, content.getQtyPages());
                String errorMsg = String.format("The number of downloaded images (%s) does not match the book's number of pages (%s)", nbDownloadedPages, content.getQtyPages());
                logErrorRecord(contentId, ErrorType.PARSING, content.getGalleryUrl(), "pages", errorMsg);
                hasError = true;
            }

            // If additional pages have been downloaded (e.g. new chapters on existing book),
            // update the book's number of pages and download date
            if (nbImages > content.getQtyPages()) {
                content.setQtyPages(nbImages);
                content.setDownloadDate(Instant.now().toEpochMilli());
            }

            if (content.getStorageUri().isEmpty()) return;

            DocumentFile dir = FileHelper.getFolderFromTreeUriString(getApplicationContext(), content.getStorageUri());
            if (dir != null) {
                // Auto-retry when error pages are remaining and conditions are met
                // NB : Differences between expected and detected pages (see block above) can't be solved by retrying - it's a parsing issue
                // TODO - test to make sure the service's thread continues to run in such a scenario
                if (pagesKO > 0 && Preferences.isDlRetriesActive()
                        && content.getNumberDownloadRetries() < Preferences.getDlRetriesNumber()) {
                    double freeSpaceRatio = new FileHelper.MemoryUsageFigures(getApplicationContext(), dir).getFreeUsageRatio100();

                    if (freeSpaceRatio < Preferences.getDlRetriesMemLimit()) {
                        Timber.i("Initiating auto-retry #%s for content %s (%s%% free space)", content.getNumberDownloadRetries() + 1, content.getTitle(), freeSpaceRatio);
                        logErrorRecord(content.getId(), ErrorType.UNDEFINED, "", content.getTitle(), "Auto-retry #" + content.getNumberDownloadRetries());
                        content.increaseNumberDownloadRetries();

                        // Re-queue all failed images
                        for (ImageFile img : images)
                            if (img.getStatus().equals(StatusContent.ERROR)) {
                                Timber.i("Auto-retry #%s for content %s / image @ %s", content.getNumberDownloadRetries(), content.getTitle(), img.getUrl());
                                img.setStatus(StatusContent.SAVED);
                                dao.insertImageFile(img);
                                requestQueueManager.queueRequest(buildDownloadRequest(img, dir, content.getSite()));
                            }
                        return;
                    }
                }

                // Compute perceptual hash for the cover picture
                Bitmap coverBitmap = DuplicateHelper.Companion.getCoverBitmapFromContent(getApplicationContext(), content);
                long pHash = DuplicateHelper.Companion.calcPhash(DuplicateHelper.Companion.getHashEngine(), coverBitmap);
                if (coverBitmap != null) coverBitmap.recycle();
                content.getCover().setImageHash(pHash);
                dao.insertImageFile(content.getCover());

                // Mark content as downloaded
                if (0 == content.getDownloadDate())
                    content.setDownloadDate(Instant.now().toEpochMilli());
                content.setStatus((0 == pagesKO && !hasError) ? StatusContent.DOWNLOADED : StatusContent.ERROR);
                // Clear download params from content
                if (0 == pagesKO && !hasError) content.setDownloadParams("");
                content.computeSize();

                // Save JSON file
                try {
                    DocumentFile jsonFile = JsonHelper.jsonToFile(getApplicationContext(), JsonContent.fromEntity(content), JsonContent.class, dir);
                    // Cache its URI to the newly created content
                    if (jsonFile != null) {
                        content.setJsonUri(jsonFile.getUri().toString());
                    } else {
                        Timber.w("JSON file could not be cached for %s", title);
                    }
                } catch (IOException e) {
                    Timber.e(e, "I/O Error saving JSON: %s", title);
                }
                ContentHelper.addContent(getApplicationContext(), dao, content);

                Timber.i("Content download finished: %s [%s]", title, contentId);

                // Delete book from queue
                dao.deleteQueue(content);

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
                EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_COMPLETE, pagesOK, pagesKO, nbImages, sizeDownloadedBytes));

                if (ContentHelper.updateQueueJson(getApplicationContext(), dao))
                    Timber.i("Queue JSON successfully saved");
                else Timber.w("Queue JSON saving failed");

                // Tracking Event (Download Completed)
                HentoidApp.trackDownloadEvent("Completed");
            } else {
                Timber.w("completeDownload : Directory %s does not exist - JSON not saved", content.getStorageUri());
            }
        } else if (downloadCanceled) {
            Timber.d("Content download canceled: %s [%s]", title, contentId);
            notificationManager.cancel();
        } else {
            Timber.d("Content download skipped : %s [%s]", title, contentId);
        }
    }

    /**
     * Query source to fetch all image file names and URLs of a given book
     *
     * @param content Book whose pages to retrieve
     * @return List of pages with original URLs and file name
     */
    private List<ImageFile> fetchImageURLs(@NonNull Content content) throws Exception {
        List<ImageFile> imgs;

        // If content doesn't have any download parameters, get them from the live gallery page
        String contentDownloadParamsStr = content.getDownloadParams();
        if (null == contentDownloadParamsStr || contentDownloadParamsStr.isEmpty()) {
            String cookieStr = HttpHelper.getCookies(content.getGalleryUrl());
            if (!cookieStr.isEmpty()) {
                Map<String, String> downloadParams = new HashMap<>();
                downloadParams.put(HttpHelper.HEADER_COOKIE_KEY, cookieStr);
                content.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));
            }
        }

        // Use ImageListParser to query the source
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(content);
        imgs = parser.parseImageList(content);

        // Add the content's download params to the images if they have missing information
        contentDownloadParamsStr = content.getDownloadParams();
        if (contentDownloadParamsStr != null && contentDownloadParamsStr.length() > 2) {
            Map<String, String> contentDownloadParams = ContentHelper.parseDownloadParams(contentDownloadParamsStr);
            for (ImageFile i : imgs) {
                if (i.getDownloadParams() != null && i.getDownloadParams().length() > 2) {
                    Map<String, String> imageDownloadParams = ContentHelper.parseDownloadParams(i.getDownloadParams());
                    // Content's
                    for (Map.Entry<String, String> entry : contentDownloadParams.entrySet())
                        if (!imageDownloadParams.containsKey(entry.getKey()))
                            imageDownloadParams.put(entry.getKey(), entry.getValue());
                    // Referer, just in case
                    if (!imageDownloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY))
                        imageDownloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getSite().getUrl());
                    i.setDownloadParams(JsonHelper.serializeToJson(imageDownloadParams, JsonHelper.MAP_STRINGS));
                } else {
                    i.setDownloadParams(contentDownloadParamsStr);
                }
            }
        }

        // If no images found, or just the cover, image detection has failed
        if (imgs.isEmpty() || (1 == imgs.size() && imgs.get(0).isCover()))
            throw new EmptyResultException();

        // Cleanup generated objects
        for (ImageFile img : imgs) {
            img.setId(0);
            img.setStatus(StatusContent.SAVED);
            img.setContentId(content.getId());
        }

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
            @NonNull final ImageFile img,
            @NonNull final DocumentFile dir,
            @NonNull final Site site) {

        String backupUrl = "";

        // Apply image download parameters
        Map<String, String> requestHeaders = new HashMap<>();
        Map<String, String> downloadParams = ContentHelper.parseDownloadParams(img.getDownloadParams());
        if (!downloadParams.isEmpty()) {
            if (downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY)) {
                String value = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY);
                if (value != null) requestHeaders.put(HttpHelper.HEADER_COOKIE_KEY, value);
            }
            if (downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY)) {
                String value = downloadParams.get(HttpHelper.HEADER_REFERER_KEY);
                if (value != null) requestHeaders.put(HttpHelper.HEADER_REFERER_KEY, value);
            }
            if (downloadParams.containsKey("backupUrl"))
                backupUrl = downloadParams.get("backupUrl");
        }
        final String backupUrlFinal = HttpHelper.fixUrl(backupUrl, site.getUrl());

        return new InputStreamVolleyRequest(
                Request.Method.GET,
                HttpHelper.fixUrl(img.getUrl(), site.getUrl()),
                requestHeaders,
                site.useHentoidAgent(),
                site.useWebviewAgent(),
                result -> onRequestSuccess(result, img, dir, site.hasImageProcessing(), backupUrlFinal, requestHeaders),
                error -> onRequestError(error, img, dir, backupUrlFinal, requestHeaders));
    }

    private void onRequestSuccess(
            Map.Entry<byte[], Map<String, String>> result,
            @NonNull ImageFile img,
            @NonNull DocumentFile dir,
            boolean hasImageProcessing,
            @NonNull String backupUrl,
            @NonNull Map<String, String> requestHeaders) {
        try {
            if (result != null) {
                DocumentFile imgFile = processAndSaveImage(img, dir, result.getValue().get(HttpHelper.HEADER_CONTENT_TYPE), result.getKey(), hasImageProcessing);
                if (imgFile != null)
                    updateImageStatusUri(img, true, imgFile.getUri().toString());
            } else {
                updateImageStatusUri(img, false, "");
                logErrorRecord(img.getContent().getTargetId(), ErrorType.UNDEFINED, img.getUrl(), img.getName(), "Result null");
            }
        } catch (UnsupportedContentException e) {
            Timber.w(e);
            if (!backupUrl.isEmpty()) tryUsingBackupUrl(img, dir, backupUrl, requestHeaders);
            else {
                Timber.w("No backup URL found - aborting this image");
                updateImageStatusUri(img, false, "");
                logErrorRecord(img.getContent().getTargetId(), ErrorType.UNDEFINED, img.getUrl(), img.getName(), e.getMessage());
            }
        } catch (InvalidParameterException e) {
            Timber.w(e, "Processing error - Image %s not processed properly", img.getUrl());
            updateImageStatusUri(img, false, "");
            logErrorRecord(img.getContent().getTargetId(), ErrorType.IMG_PROCESSING, img.getUrl(), img.getName(), "Download params : " + img.getDownloadParams());
        } catch (IOException | IllegalArgumentException e) {
            Timber.w(e, "I/O error - Image %s not saved in dir %s", img.getUrl(), dir.getUri());
            updateImageStatusUri(img, false, "");
            logErrorRecord(img.getContent().getTargetId(), ErrorType.IO, img.getUrl(), img.getName(), "Save failed in dir " + dir.getUri() + " " + e.getMessage());
        }
    }

    private void onRequestError(
            VolleyError error,
            @NonNull ImageFile img,
            @NonNull DocumentFile dir,
            @NonNull String backupUrl,
            @NonNull Map<String, String> requestHeaders) {
        // Try with the backup URL, if it exists and if the current image isn't a backup itself
        if (!img.isBackup() && !backupUrl.isEmpty()) {
            tryUsingBackupUrl(img, dir, backupUrl, requestHeaders);
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
        } else if (error instanceof AuthFailureError) { // 403's fall in this category
            cause = "Auth failure";
        } else if (error instanceof ServerError) { // 404's fall in this category
            cause = "Server error";
        } else if (error instanceof NetworkError) {
            cause = "Network error";
        } else if (error instanceof ParseError) {
            cause = "Network parse error";
        }

        Timber.w(error);

        updateImageStatusUri(img, false, "");
        logErrorRecord(img.getContent().getTargetId(), ErrorType.NETWORKING, img.getUrl(), img.getName(), cause + "; HTTP statusCode=" + statusCode + "; message=" + message);
    }

    private void tryUsingBackupUrl(
            @NonNull ImageFile img,
            @NonNull DocumentFile dir,
            @NonNull String backupUrl,
            @NonNull Map<String, String> requestHeaders) {
        Timber.i("Using backup URL %s", backupUrl);
        Content content = img.getContent().getTarget();
        if (null == content) return;

        Site site = content.getSite();
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(site);

        // per Volley behaviour, this method is called on the UI thread
        // -> need to create a new thread to do a network call
        compositeDisposable.add(
                Single.fromCallable(() -> parser.parseBackupUrl(backupUrl, requestHeaders, img.getOrder(), content.getQtyPages()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.computation())
                        .subscribe(
                                imageFile -> processBackupImage(imageFile.orElse(null), img, dir, site),
                                throwable ->
                                {
                                    updateImageStatusUri(img, false, "");
                                    logErrorRecord(img.getContent().getTargetId(), ErrorType.NETWORKING, img.getUrl(), img.getName(), "Cannot process backup image : message=" + throwable.getMessage());
                                    Timber.e(throwable, "Error processing backup image.");
                                }
                        )
        );
    }

    private void processBackupImage(ImageFile backupImage, @NonNull ImageFile
            originalImage, @NonNull DocumentFile dir, Site site) {
        if (backupImage != null) {
            Timber.i("Backup URL contains image @ %s; queuing", backupImage.getUrl());
            originalImage.setUrl(backupImage.getUrl()); // Replace original image URL by backup image URL
            originalImage.setBackup(true); // Indicates the image is from a backup (for display in error logs)
            dao.insertImageFile(originalImage);
            requestQueueManager.queueRequest(buildDownloadRequest(originalImage, dir, site));
        } else Timber.w("Failed to parse backup URL");
    }

    private static byte[] processImage(String downloadParamsStr, byte[] binaryContent) throws
            IOException {
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
    private DocumentFile processAndSaveImage(@NonNull ImageFile img,
                                             @NonNull DocumentFile dir,
                                             @Nullable String contentType,
                                             byte[] binaryContent,
                                             boolean hasImageProcessing) throws IOException, UnsupportedContentException {

        if (!dir.exists()) {
            // NB : Should not raise an exception here because that's what happens when some previously queued downloads are completed
            // after a queued book has been manually deleted (and its folder with it)
            Timber.w("processAndSaveImage : Directory %s does not exist - image not saved", dir.getUri().toString());
            return null;
        }

        byte[] processedBinaryContent = null;
        if (hasImageProcessing && !img.getName().equals(Consts.THUMB_FILE_NAME)) {
            if (img.getDownloadParams() != null && !img.getDownloadParams().isEmpty())
                processedBinaryContent = processImage(img.getDownloadParams(), binaryContent);
            else throw new InvalidParameterException("No processing parameters found");
        }
        binaryContent = (null == processedBinaryContent) ? binaryContent : processedBinaryContent;
        img.setSize((null == binaryContent) ? 0 : binaryContent.length);

        // Determine the extension of the file
        String fileExt = null;
        String mimeType = null;

        // Check for picture validity if it's < 1KB (might be plain test or HTML if things have gone wrong... or a small GIF! )
        if (img.getSize() < 1024 && binaryContent != null) {
            mimeType = ImageHelper.getMimeTypeFromPictureBinary(binaryContent);
            if (mimeType.isEmpty() || mimeType.equals(ImageHelper.MIME_IMAGE_GENERIC)) {
                Timber.w("Small non-image data received from %s", img.getUrl());
                throw new UnsupportedContentException(String.format("Small non-image data received from %s - data not processed", img.getUrl()));
            }
            fileExt = FileHelper.getExtensionFromMimeType(mimeType);
        }

        // Use the Content-type contained in the HTTP headers of the response
        if (null != contentType) {
            mimeType = HttpHelper.cleanContentType(contentType).first;
            // Ignore neutral binary content-type
            if (!contentType.equalsIgnoreCase("application/octet-stream")) {
                fileExt = FileHelper.getExtensionFromMimeType(contentType);
                Timber.d("Using content-type %s to determine file extension -> %s", contentType, fileExt);
            }
        }
        // Content-type has not been useful to determine the extension => See if the URL contains an extension
        if (null == fileExt || fileExt.isEmpty()) {
            fileExt = HttpHelper.getExtensionFromUri(img.getUrl());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
            Timber.d("Using url to determine file extension (content-type was %s) for %s -> %s", contentType, img.getUrl(), fileExt);
        }
        // No extension detected in the URL => Read binary header of the file to detect known formats
        // If PNG, peek into the file to see if it is an animated PNG or not (no other way to do that)
        if (binaryContent != null && (fileExt.isEmpty() || fileExt.equals("png"))) {
            mimeType = ImageHelper.getMimeTypeFromPictureBinary(binaryContent);
            fileExt = FileHelper.getExtensionFromMimeType(mimeType);
            Timber.d("Reading headers to determine file extension for %s -> %s (from detected mime-type %s)", img.getUrl(), fileExt, mimeType);
        }
        // If all else fails, fall back to jpg as default
        if (null == fileExt || fileExt.isEmpty()) {
            fileExt = "jpg";
            mimeType = ImageHelper.MIME_IMAGE_JPEG;
            Timber.d("Using default extension for %s -> %s", img.getUrl(), fileExt);
        }
        if (null == mimeType) mimeType = ImageHelper.MIME_IMAGE_GENERIC;
        img.setMimeType(mimeType);

        if (!ImageHelper.isImageExtensionSupported(fileExt))
            throw new UnsupportedContentException(String.format("Unsupported extension %s for %s - data not processed", fileExt, img.getUrl()));

        return saveImage(dir, img.getName() + "." + fileExt, mimeType, binaryContent);
    }

    /**
     * Create the given file in the given destination folder, and write binary data to it
     *
     * @param dir           Destination folder
     * @param fileName      Name of the file to write (with the extension)
     * @param binaryContent Binary content of the image
     * @throws IOException IOException if image cannot be saved at given location
     */
    private DocumentFile saveImage(
            @NonNull DocumentFile dir,
            @NonNull String fileName,
            @NonNull String mimeType,
            byte[] binaryContent) throws IOException {
        DocumentFile file = FileHelper.findOrCreateDocumentFile(getApplicationContext(), dir, mimeType, fileName);
        if (null == file)
            throw new IOException(String.format("Failed to create document %s under %s", fileName, dir.getUri().toString()));
        FileHelper.saveBinary(getApplicationContext(), file.getUri(), binaryContent);
        return file;
    }

    /**
     * Update given image status in DB
     *
     * @param img     Image to update
     * @param success True if download is successful; false if download failed
     */
    private void updateImageStatusUri(@NonNull ImageFile img, boolean success,
                                      @NonNull String uriStr) {
        img.setStatus(success ? StatusContent.DOWNLOADED : StatusContent.ERROR);
        img.setFileUri(uriStr);
        if (success) img.setDownloadParams("");
        if (img.getId() > 0)
            dao.updateImageFileStatusParamsMimeTypeUriSize(img); // because thumb image isn't in the DB
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
                dao.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
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
                dao.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                requestQueueManager.cancelQueue();
                downloadSkipped = true;
                // Tracking Event (Download Skipped)
                HentoidApp.trackDownloadEvent("Skipped");
                break;
            default:
                // Other events aren't handled here
        }
    }

    private void logErrorRecord(long contentId, ErrorType type, String url, String
            contentPart, String description) {
        ErrorRecord record = new ErrorRecord(contentId, type, url, contentPart, description, Instant.now());
        if (contentId > 0) dao.insertErrorRecord(record);
    }

    private void moveToErrors(long contentId) {
        Content content = dao.selectContent(contentId);
        if (null == content) return;

        content.setStatus(StatusContent.ERROR);
        content.setDownloadDate(Instant.now().toEpochMilli()); // Needs a download date to appear the right location when sorted by download date
        dao.insertContent(content);
        dao.deleteQueue(content);
        HentoidApp.trackDownloadEvent("Error");

        if (ContentHelper.updateQueueJson(getApplicationContext(), dao))
            Timber.i("Queue JSON successfully saved");
        else Timber.w("Queue JSON saving failed");

        notificationManager.notify(new DownloadErrorNotification(content));
    }
}
