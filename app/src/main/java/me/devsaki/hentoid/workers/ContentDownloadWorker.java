package me.devsaki.hentoid.workers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.RenamingRule;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadReviveEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.notification.action.UserActionNotification;
import me.devsaki.hentoid.notification.download.DownloadErrorNotification;
import me.devsaki.hentoid.notification.download.DownloadProgressNotification;
import me.devsaki.hentoid.notification.download.DownloadSuccessNotification;
import me.devsaki.hentoid.notification.download.DownloadWarningNotification;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.download.DownloadHelper;
import me.devsaki.hentoid.util.download.RequestOrder;
import me.devsaki.hentoid.util.download.RequestQueueManager;
import me.devsaki.hentoid.util.exception.AccountException;
import me.devsaki.hentoid.util.exception.CaptchaException;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.file.ArchiveHelper;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.image.ImageHelper;
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.network.NetworkHelper;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.util.notification.NotificationManager;
import timber.log.Timber;

public class ContentDownloadWorker extends BaseWorker {

    private enum QueuingResult {
        CONTENT_FOUND, CONTENT_SKIPPED, CONTENT_FAILED, QUEUE_END
    }

    private static final int IDLE_THRESHOLD = 20; // seconds; should be higher than the connect + I/O timeout defined in RequestQueueManager
    private static final int LOW_NETWORK_THRESHOLD = 10; // KBps

    // DAO is full scope to avoid putting try / finally's everywhere and be sure to clear it upon worker stop
    private final CollectionDAO dao;

    // True if a Cancel event has been processed; false by default
    private final AtomicBoolean downloadCanceled = new AtomicBoolean(false);
    // True if a Skip event has been processed; false by default
    private final AtomicBoolean downloadSkipped = new AtomicBoolean(false);
    // downloadCanceled || downloadSkipped
    private final AtomicBoolean downloadInterrupted = new AtomicBoolean(false);
    private boolean isCloudFlareBlocked;

    private final NotificationManager userActionNotificationManager;
    private final RequestQueueManager requestQueueManager;
    protected final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Download speed calculator
    private final DownloadSpeedCalculator downloadSpeedCalculator = new DownloadSpeedCalculator();


    public ContentDownloadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.download_service, null);

        EventBus.getDefault().register(this);
        dao = new ObjectBoxDAO(context);

        requestQueueManager = RequestQueueManager.Companion.getInstance(context, this::onRequestSuccess, this::onRequestError);
        userActionNotificationManager = new NotificationManager(context, R.id.user_action_notification);
    }

    @Override
    Notification getStartNotification() {
        String message = getApplicationContext().getResources().getString(R.string.starting_download);
        return new DownloadProgressNotification(message, 0, 0, 0, 0, 0);
    }

    @Override
    void onInterrupt() {
        requestQueueManager.cancelQueue();
        downloadCanceled.set(true);
        downloadInterrupted.set(true);
    }

    @Override
    void onClear() {
        EventBus.getDefault().unregister(this);
        compositeDisposable.clear();

        if (dao != null) dao.cleanup();
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.download_service);
    }

    @Override
    void getToWork(@NonNull Data input) {
        iterateQueue();
    }

    private void iterateQueue() {
        // Process these here to avoid initializing notifications for downloads that will never start
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.i("Queue is paused. Download aborted.");
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
     * NB : This method is not only called the 1st time the queue is awakened,
     * but also after every book has finished downloading
     *
     * @return Pair containing
     * - Left : Result of the processing
     * - Right : 1st book of the download queue; null if no book is available to download
     */
    @SuppressLint({"TimberExceptionLogging", "TimberArgCount"})
    @NonNull
    private ImmutablePair<QueuingResult, Content> downloadFirstInQueue() {
        final String CONTENT_PART_IMAGE_LIST = "Image list";

        Context context = getApplicationContext();

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.INIT));

        // Clear previously created requests
        compositeDisposable.clear();

        // Check if queue has been paused
        if (ContentQueueManager.getInstance().isQueuePaused()) {
            Timber.i("Queue is paused. Download aborted.");
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        @NetworkHelper.Connectivity int connectivity = NetworkHelper.getConnectivity(context);
        // Check for network connectivity
        if (NetworkHelper.Connectivity.NO_INTERNET == connectivity) {
            Timber.i("No internet connection available. Queue paused.");
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_INTERNET));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        // Check for wifi if wifi-only mode is on
        if (Preferences.isQueueWifiOnly() && NetworkHelper.Connectivity.WIFI != connectivity) {
            Timber.i("No wi-fi connection available. Queue paused.");
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_WIFI));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        // Check for download folder existence, available free space and credentials
        if (Preferences.getStorageUri().trim().isEmpty()) {
            Timber.i("No download folder set"); // May happen if user has skipped it during the intro
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_DOWNLOAD_FOLDER));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
        if (null == rootFolder) {
            Timber.i("Download folder has not been found. Please select it again."); // May happen if the folder has been moved or deleted after it has been selected
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.DOWNLOAD_FOLDER_NOT_FOUND));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        if (!FileHelper.isUriPermissionPersisted(context.getContentResolver(), rootFolder.getUri())) {
            Timber.i("Insufficient credentials on download folder. Please select it again.");
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.DOWNLOAD_FOLDER_NO_CREDENTIALS));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        long spaceLeftBytes = new FileHelper.MemoryUsageFigures(context, rootFolder).getfreeUsageBytes();
        if (spaceLeftBytes < 2L * 1024 * 1024) {
            Timber.i("Device very low on storage space (<2 MB). Queue paused.");
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_STORAGE, spaceLeftBytes));
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        // Work on first item of queue

        // Check if there is a first item to process
        List<QueueRecord> queue = dao.selectQueue();
        if (queue.isEmpty()) {
            Timber.i("Queue is empty. Download aborted.");
            return new ImmutablePair<>(QueuingResult.QUEUE_END, null);
        }

        Content content = queue.get(0).getContent().getTarget();

        if (null == content) {
            Timber.i("Content is unavailable. Download aborted.");
            dao.deleteQueue(0);
            content = new Content().setId(queue.get(0).getContent().getTargetId()); // Must supply content ID to the event for the UI to update properly
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.Type.EV_COMPLETE, 0, 0, 0, 0));
            notificationManager.notify(new DownloadErrorNotification());
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
        }

        if (StatusContent.DOWNLOADED == content.getStatus()) {
            Timber.i("Content is already downloaded. Download aborted.");
            dao.deleteQueue(0);
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.Type.EV_COMPLETE, 0, 0, 0, 0));
            notificationManager.notify(new DownloadErrorNotification(content));
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
        }

        downloadCanceled.set(false);
        downloadSkipped.set(false);
        downloadInterrupted.set(false);
        isCloudFlareBlocked = false;
        @Content.DownloadMode int downloadMode = content.getDownloadMode();
        dao.deleteErrorRecords(content.getId());

        // == PREPARATION PHASE ==
        // Parse images from the site (using image list parser)
        //   - Case 1 : If no image is present => parse all images
        //   - Case 2 : If all images are in ERROR state => re-parse all images
        //   - Case 3 : If some images are in ERROR state and the site has backup URLs
        //     => re-parse images with ERROR state using their order as reference
        boolean hasError = false;
        int nbErrors = 0;

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.PROCESS_IMG));

        List<ImageFile> images = content.getImageFiles();
        if (null == images)
            images = new ArrayList<>();
        else
            images = new ArrayList<>(images); // Safe copy of the original list

        for (ImageFile img : images) if (img.getStatus().equals(StatusContent.ERROR)) nbErrors++;
        StatusContent targetImageStatus = (downloadMode == Content.DownloadMode.DOWNLOAD) ? StatusContent.SAVED : StatusContent.ONLINE;

        if (images.isEmpty()
                || nbErrors == images.size()
                || (nbErrors > 0 && content.getSite().hasBackupURLs())
        ) {
            EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.FETCH_IMG));
            try {
                List<ImageFile> newImages = ContentHelper.fetchImageURLs(content, targetImageStatus);
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

                if (content.isUpdatedProperties()) dao.insertContent(content);

                // Manually insert new images (without using insertContent)
                dao.replaceImageList(content.getId(), images);
            } catch (CaptchaException cpe) {
                Timber.i(cpe, "A captcha has been found while parsing %s. Download aborted.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.CAPTCHA, content.getUrl(), CONTENT_PART_IMAGE_LIST, "Captcha found. Please go back to the site, browse a book and solve the captcha.");
                hasError = true;
            } catch (AccountException ae) {
                String description = String.format("Your %s account does not allow to download the book %s. %s. Download aborted.", content.getSite().getDescription(), content.getTitle(), ae.getMessage());
                Timber.i(ae, description);
                logErrorRecord(content.getId(), ErrorType.ACCOUNT, content.getUrl(), CONTENT_PART_IMAGE_LIST, description);
                hasError = true;
            } catch (LimitReachedException lre) {
                String description = String.format("The bandwidth limit has been reached while parsing %s. %s. Download aborted.", content.getTitle(), lre.getMessage());
                Timber.i(lre, description);
                logErrorRecord(content.getId(), ErrorType.SITE_LIMIT, content.getUrl(), CONTENT_PART_IMAGE_LIST, description);
                hasError = true;
            } catch (PreparationInterruptedException ie) {
                Timber.i(ie, "Preparation of %s interrupted", content.getTitle());
                // not an error
            } catch (EmptyResultException ere) {
                Timber.i(ere, "No images have been found while parsing %s. Download aborted.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), CONTENT_PART_IMAGE_LIST, "No images have been found. Error = " + ere.getMessage());
                hasError = true;
            } catch (Exception e) {
                Timber.w(e, "An exception has occurred while parsing %s. Download aborted.", content.getTitle());
                logErrorRecord(content.getId(), ErrorType.PARSING, content.getUrl(), CONTENT_PART_IMAGE_LIST, e.getMessage());
                hasError = true;
            }
        } else if (nbErrors > 0) {
            // Other cases : Reset ERROR status of images to mark them as "to be downloaded" (in DB and in memory)
            dao.updateImageContentStatus(content.getId(), StatusContent.ERROR, targetImageStatus);
        } else {
            if (downloadMode == Content.DownloadMode.STREAM)
                dao.updateImageContentStatus(content.getId(), null, StatusContent.ONLINE);
        }

        // Get updated Content with the udpated ID and status of new images
        content = dao.selectContent(content.getId());
        if (null == content)
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);

        if (hasError) {
            moveToErrors(content.getId());
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.Type.EV_COMPLETE, 0, 0, 0, 0));
            return new ImmutablePair<>(QueuingResult.CONTENT_FAILED, content);
        }

        // In case the download has been canceled while in preparation phase
        // NB : No log of any sort because this is normal behaviour
        if (downloadInterrupted.get())
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.PREPARE_FOLDER));

        // Create destination folder for images to be downloaded
        DocumentFile dir = ContentHelper.getOrCreateContentDownloadDir(getApplicationContext(), content, false, null);
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
            dao.updateImageContentStatus(content.getId(), targetImageStatus, StatusContent.ERROR);
            completeDownload(content.getId(), content.getTitle(), 0, images.size(), 0);
            return new ImmutablePair<>(QueuingResult.CONTENT_FAILED, content);
        }

        // Folder creation succeeds -> memorize its path
        content.setStorageUri(dir.getUri().toString());
        // Set QtyPages if the content parser couldn't do it (certain sources only)
        // Don't count the cover thumbnail in the number of pages
        if (0 == content.getQtyPages()) content.setQtyPages(images.size() - 1);
        content.setStatus(StatusContent.DOWNLOADING);
        // Mark the cover for downloading when saving a streamed book
        if (downloadMode == Content.DownloadMode.STREAM)
            content.getCover().setStatus(StatusContent.SAVED);
        dao.insertContent(content);

        HentoidApp.trackDownloadEvent("Added");
        Timber.i("Downloading '%s' [%s]", content.getTitle(), content.getId());

        // Wait until the end of purge if the content is being purged (e.g. redownload from scratch)
        boolean isBeingDeleted = content.isBeingDeleted();
        if (isBeingDeleted)
            EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.WAIT_PURGE));
        while (content.isBeingDeleted()) {
            Timber.d("Waiting for purge to complete");
            content = dao.selectContent(content.getId());
            if (null == content)
                return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);
            Helper.pause(1000);
            if (downloadInterrupted.get()) break;
        }
        if (isBeingDeleted && !downloadInterrupted.get())
            Timber.d("Purge completed; resuming download");


        // == DOWNLOAD PHASE ==

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.PREPARE_DOWNLOAD));

        // Set up downloader constraints
        if (content.getSite().getParallelDownloadCap() > 0 &&
                (requestQueueManager.getDownloadThreadCap() > content.getSite().getParallelDownloadCap()
                        || -1 == requestQueueManager.getDownloadThreadCap())
        ) {
            Timber.d("Setting parallel downloads count to %s", content.getSite().getParallelDownloadCap());
            requestQueueManager.initUsingDownloadThreadCount(getApplicationContext(), content.getSite().getParallelDownloadCap(), true);
        }
        if (0 == content.getSite().getParallelDownloadCap() && requestQueueManager.getDownloadThreadCap() > -1) {
            Timber.d("Resetting parallel downloads count to default");
            requestQueueManager.initUsingDownloadThreadCount(getApplicationContext(), -1, true);
        }
        requestQueueManager.setNbRequestsPerSecond(content.getSite().getRequestsCapPerSecond());

        // In case the download has been canceled while in preparation phase
        // NB : No log of any sort because this is normal behaviour
        if (downloadInterrupted.get())
            return new ImmutablePair<>(QueuingResult.CONTENT_SKIPPED, null);

        List<ImageFile> pagesToParse = new ArrayList<>();
        List<ImageFile> ugoirasToDownload = new ArrayList<>();

        // Just get the cover if we're in a streamed download
        if (downloadMode == Content.DownloadMode.STREAM) {
            Optional<ImageFile> coverOptional = Stream.of(images).filter(ImageFile::isCover).findFirst();
            if (coverOptional.isPresent()) {
                ImageFile cover = coverOptional.get();
                enrichImageDownloadParams(cover, content);
                requestQueueManager.queueRequest(buildImageDownloadRequest(cover, dir, content));
            }
        } else { // Regular downloads

            // Queue image download requests
            for (ImageFile img : images) {
                if (img.getStatus().equals(StatusContent.SAVED)) {

                    enrichImageDownloadParams(img, content);

                    // Set the 1st image of the list as a backup in case the cover URL is stale (might happen when restarting old downloads)
                    if (img.isCover() && images.size() > 1)
                        img.setBackupUrl(images.get(1).getUrl());

                    if (img.needsPageParsing()) pagesToParse.add(img);
                    else if (img.getDownloadParams().contains(ContentHelper.KEY_DL_PARAMS_UGOIRA_FRAMES))
                        ugoirasToDownload.add(img);
                    else
                        requestQueueManager.queueRequest(buildImageDownloadRequest(img, dir, content));
                }
            }

            // Parse pages for images
            if (!pagesToParse.isEmpty()) {
                final Content contentFinal = content;
                compositeDisposable.add(
                        Observable.fromIterable(pagesToParse)
                                .observeOn(Schedulers.io())
                                .subscribe(
                                        img -> parsePageforImage(img, dir, contentFinal),
                                        t -> {
                                            // Nothing; just exit the Rx chain
                                        }
                                )
                );
            }

            // Parse ugoiras for images
            if (!ugoirasToDownload.isEmpty()) {
                final Site siteFinal = content.getSite();
                compositeDisposable.add(
                        Observable.fromIterable(ugoirasToDownload)
                                .observeOn(Schedulers.io())
                                .subscribe(
                                        img -> downloadAndUnzipUgoira(img, dir, siteFinal),
                                        t -> {
                                            // Nothing; just exit the Rx chain
                                        }
                                )
                );
            }
        }

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.SAVE_QUEUE));

        if (ContentHelper.updateQueueJson(getApplicationContext(), dao))
            Timber.i(context.getString(R.string.queue_json_saved));
        else Timber.w(context.getString(R.string.queue_json_failed));

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.START_DOWNLOAD));

        return new ImmutablePair<>(QueuingResult.CONTENT_FOUND, content);
    }

    private void enrichImageDownloadParams(@NonNull ImageFile img, @NonNull Content content) {
        // Enrich download params just in case
        Map<String, String> downloadParams;
        if (img.getDownloadParams().length() > 2)
            downloadParams = ContentHelper.parseDownloadParams(img.getDownloadParams());
        else
            downloadParams = new HashMap<>();
        // Add referer if unset
        if (!downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY))
            downloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getGalleryUrl());
        // Add cookies if unset or if the site needs fresh cookies
        if (!downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY) || content.getSite().isUseCloudflare())
            downloadParams.put(HttpHelper.HEADER_COOKIE_KEY, HttpHelper.getCookies(img.getUrl()));

        img.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));
    }

    /**
     * Watch download progress
     * <p>
     * NB : download pause is managed at the Request queue level (see RequestQueueManager.pauseQueue / startQueue)
     *
     * @param content Content to watch (1st book of the download queue)
     */
    private void watchProgress(@NonNull Content content) {
        boolean isDone;
        int pagesOK = 0;
        int pagesKO = 0;
        long downloadedBytes = 0;

        boolean firstPageDownloaded = false;
        int deltaPages = 0;
        int nbDeltaZeroPages = 0;
        long networkBytes = 0;
        long deltaNetworkBytes;
        int nbDeltaLowNetwork = 0;

        List<ImageFile> images = content.getImageFiles();
        // Compute total downloadable pages; online (stream) pages do not count
        int totalPages = (null == images) ? 0 : (int) Stream.of(images).filter(i -> !i.getStatus().equals(StatusContent.ONLINE)).count();

        ContentQueueManager contentQueueManager = ContentQueueManager.getInstance();
        do {
            Map<StatusContent, ImmutablePair<Integer, Long>> statuses = dao.countProcessedImagesById(content.getId());
            ImmutablePair<Integer, Long> status = statuses.get(StatusContent.DOWNLOADED);

            // Measure idle time since last iteration
            if (status != null) {
                deltaPages = status.left - pagesOK;
                if (deltaPages == 0) nbDeltaZeroPages++;
                else {
                    firstPageDownloaded = true;
                    nbDeltaZeroPages = 0;
                }
                pagesOK = status.left;
                downloadedBytes = status.right;
            }
            status = statuses.get(StatusContent.ERROR);
            if (status != null)
                pagesKO = status.left;

            double downloadedMB = downloadedBytes / (1024.0 * 1024);
            int progress = pagesOK + pagesKO;
            isDone = progress == totalPages;
            Timber.d("Progress: OK:%d size:%dMB - KO:%d - Total:%d", pagesOK, (int) downloadedMB, pagesKO, totalPages);

            // Download speed and size estimation
            long networkBytesNow = NetworkHelper.getIncomingNetworkUsage(getApplicationContext());
            deltaNetworkBytes = networkBytesNow - networkBytes;
            if (deltaNetworkBytes < 1024 * LOW_NETWORK_THRESHOLD && firstPageDownloaded)
                nbDeltaLowNetwork++; // LOW_NETWORK_THRESHOLD KBps threshold once download has started
            else nbDeltaLowNetwork = 0;
            networkBytes = networkBytesNow;
            downloadSpeedCalculator.addSampleNow(networkBytes);
            int avgSpeedKbps = (int) downloadSpeedCalculator.getAvgSpeedKbps();

            Timber.d("deltaPages: %d / deltaNetworkBytes: %s", deltaPages, FileHelper.formatHumanReadableSize(deltaNetworkBytes, getApplicationContext().getResources()));
            Timber.d("nbDeltaZeroPages: %d / nbDeltaLowNetwork: %d", nbDeltaZeroPages, nbDeltaLowNetwork);

            // Restart request queue when the queue has idled for too long
            // Idle = very low download speed _AND_ no new pages downloaded
            if (nbDeltaLowNetwork > IDLE_THRESHOLD && nbDeltaZeroPages > IDLE_THRESHOLD) {
                nbDeltaLowNetwork = 0;
                nbDeltaZeroPages = 0;
                Timber.d("Inactivity detected ====> estarting request queue");
                requestQueueManager.resetRequestQueue(false);
            }

            double estimateBookSizeMB = -1;
            if (pagesOK > 3 && progress > 0 && totalPages > 0) {
                estimateBookSizeMB = downloadedMB / (progress * 1.0 / totalPages);
                Timber.v("Estimate book size calculated for wifi check : %s MB", estimateBookSizeMB);
            }

            notificationManager.notify(new DownloadProgressNotification(content.getTitle(), progress, totalPages, (int) downloadedMB, (int) estimateBookSizeMB, avgSpeedKbps));
            EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.Type.EV_PROGRESS, pagesOK, pagesKO, totalPages, downloadedBytes));

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
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.Type.EV_SKIP));
                }
            }

            // We're polling the DB because we can't observe LiveData from a background service
            Helper.pause(1000);
        }
        while (!isDone && !downloadInterrupted.get() && !contentQueueManager.isQueuePaused());

        if (contentQueueManager.isQueuePaused()) {
            Timber.d("Content download paused : %s [%s]", content.getTitle(), content.getId());
            if (downloadCanceled.get()) notificationManager.cancel();
        } else {
            // NB : no need to supply the Content itself as it has not been updated during the loop
            completeDownload(content.getId(), content.getTitle(), pagesOK, pagesKO, downloadedBytes);
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

        EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.COMPLETE_DOWNLOAD));

        if (!downloadInterrupted.get()) {
            List<ImageFile> images = content.getImageFiles();
            if (null == images) images = Collections.emptyList();
            int nbImages = (int) Stream.of(images).filter(i -> !i.isCover()).count(); // Don't count the cover

            boolean hasError = false;
            // Set error state if no image has been detected at all
            if (0 == content.getQtyPages() && 0 == nbImages) {
                logErrorRecord(contentId, ErrorType.PARSING, content.getGalleryUrl(), "pages", "The book has no pages");
                hasError = true;
            }
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
                                requestQueueManager.queueRequest(buildImageDownloadRequest(img, dir, content));
                            }
                        return;
                    }
                }

                // Compute perceptual hash for the cover picture
                ContentHelper.computeAndSaveCoverHash(getApplicationContext(), content, dao);

                // Mark content as downloaded (download processing date; if none set before)
                if (0 == content.getDownloadDate())
                    content.setDownloadDate(Instant.now().toEpochMilli());

                if (0 == pagesKO && !hasError) {
                    content.setDownloadParams("");
                    content.setDownloadCompletionDate(Instant.now().toEpochMilli());
                    content.setStatus(StatusContent.DOWNLOADED);

                    // Delete the duplicate book that was meant to be replaced, if any
                    if (!content.getContentToReplace().isNull()) {
                        Content contentToReplace = content.getContentToReplace().getTarget();
                        if (contentToReplace != null) {
                            EventBus.getDefault().post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.REMOVE_DUPLICATE));
                            try {
                                ContentHelper.removeContent(getApplicationContext(), dao, contentToReplace);
                            } catch (ContentNotProcessedException e) {
                                Timber.w(e);
                            }
                        }
                    }
                    applyRenamingRules(content);
                } else {
                    content.setStatus(StatusContent.ERROR);
                }
                content.computeSize();

                // Save JSON file
                try {
                    DocumentFile jsonFile = JsonHelper.jsonToFile(getApplicationContext(), JsonContent.fromEntity(content), JsonContent.class, dir, Consts.JSON_FILE_NAME_V2);
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
                EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.Type.EV_COMPLETE, pagesOK, pagesKO, nbImages, sizeDownloadedBytes));

                Context context = getApplicationContext();
                if (ContentHelper.updateQueueJson(context, dao))
                    Timber.i(context.getString(R.string.queue_json_saved));
                else Timber.w(context.getString(R.string.queue_json_failed));

                // Tracking Event (Download Completed)
                HentoidApp.trackDownloadEvent("Completed");
            } else {
                Timber.w("completeDownload : Directory %s does not exist - JSON not saved", content.getStorageUri());
            }
        } else if (downloadCanceled.get()) {
            Timber.d("Content download canceled: %s [%s]", title, contentId);
            notificationManager.cancel();
        } else {
            Timber.d("Content download skipped : %s [%s]", title, contentId);
        }
    }

    /**
     * Parse the given ImageFile's page URL for the image and save it to the given folder
     *
     * @param img     Image to parse
     * @param dir     Folder to save the resulting image to
     * @param content Correponding content
     * @throws LimitReachedException in case the download limit of the site is reached
     */
    @SuppressLint("TimberArgCount")
    private void parsePageforImage(
            @NonNull final ImageFile img,
            @NonNull final DocumentFile dir,
            @NonNull final Content content) throws LimitReachedException {

        Site site = content.getSite();
        String pageUrl = HttpHelper.fixUrl(img.getPageUrl(), site.getUrl());

        // Apply image download parameters
        Map<String, String> requestHeaders = getRequestHeaders(pageUrl, img.getDownloadParams());

        try {
            List<Pair<String, String>> reqHeaders = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, img.getPageUrl());
            ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(content.getSite());
            ImmutablePair<String, Optional<String>> pages = parser.parseImagePage(img.getPageUrl(), reqHeaders);
            img.setUrl(pages.left);
            // Set backup URL
            if (pages.right.isPresent()) img.setBackupUrl(pages.right.get());
            // Queue the picture
            requestQueueManager.queueRequest(buildImageDownloadRequest(img, dir, content));
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            Timber.i(e, "Could not read image from page %s", img.getPageUrl());
            updateImageProperties(img, false, "");
            logErrorRecord(content.getId(), ErrorType.PARSING, img.getPageUrl(), "Page " + img.getName(), "Could not read image from page " + img.getPageUrl() + " " + e.getMessage());
        } catch (IOException ioe) {
            Timber.i(ioe, "Could not read page data from %s", img.getPageUrl());
            updateImageProperties(img, false, "");
            logErrorRecord(content.getId(), ErrorType.IO, img.getPageUrl(), "Page " + img.getName(), "Could not read page data from " + img.getPageUrl() + " " + ioe.getMessage());
        } catch (LimitReachedException lre) {
            String description = String.format("The bandwidth limit has been reached while parsing %s. %s. Download aborted.", content.getTitle(), lre.getMessage());
            Timber.i(lre, description);
            updateImageProperties(img, false, "");
            logErrorRecord(content.getId(), ErrorType.SITE_LIMIT, content.getUrl(), "Page " + img.getName(), description);
            throw lre;
        } catch (EmptyResultException ere) {
            Timber.i(ere, "No images have been found while parsing %s", content.getTitle());
            updateImageProperties(img, false, "");
            logErrorRecord(content.getId(), ErrorType.PARSING, img.getPageUrl(), "Page " + img.getName(), "No images have been found. Error = " + ere.getMessage());
        }
    }

    private RequestOrder buildImageDownloadRequest(
            @NonNull final ImageFile img,
            @NonNull final DocumentFile dir,
            @NonNull final Content content) {

        Site site = content.getSite();
        String imageUrl = HttpHelper.fixUrl(img.getUrl(), site.getUrl());

        // Apply image download parameters
        Map<String, String> requestHeaders = getRequestHeaders(imageUrl, img.getDownloadParams());

        final String backupUrlFinal = HttpHelper.fixUrl(img.getBackupUrl(), site.getUrl());

        return new RequestOrder(
                RequestOrder.HttpMethod.GET,
                imageUrl,
                requestHeaders,
                site,
                dir,
                img.getName(),
                img.getOrder(),
                backupUrlFinal,
                img
        );
    }

    // This is run on the I/O thread pool spawned by the downloader
    private void onRequestSuccess(RequestOrder request, Uri fileUri) {
        ImageFile img = request.getImg();
        DocumentFile imgFile = FileHelper.getFileFromSingleUriString(getApplicationContext(), fileUri.toString());
        if (imgFile != null) {
            img.setSize(imgFile.length());
            img.setMimeType(imgFile.getType());
            updateImageProperties(img, true, fileUri.toString());
        } else {
            Timber.i("I/O error - Image %s not saved in dir %s", img.getUrl(), request.getTargetDir().getUri().getPath());
            updateImageProperties(img, false, "");
            logErrorRecord(img.getContent().getTargetId(), ErrorType.IO, img.getUrl(), "Picture " + img.getName(), "Save failed in dir " + request.getTargetDir().getUri().getPath());
        }
    }

    // This is run on the I/O thread pool spawned by the downloader
    private void onRequestError(RequestOrder request, RequestOrder.NetworkError error) {
        ImageFile img = request.getImg();
        long contentId = img.getContentId();

        // Try with the backup URL, if it exists and if the current image isn't a backup itself
        if (!img.isBackup() && !request.getBackupUrl().isEmpty()) {
            tryUsingBackupUrl(img, request.getTargetDir(), request.getBackupUrl(), request.getHeaders());
            return;
        }

        // If no backup, then process the error
        int statusCode = error.getStatusCode();
        String message = error.getMessage() + (img.isBackup() ? " (from backup URL)" : "");
        String cause = "Network error";

        Timber.d(message + " " + cause);

        updateImageProperties(img, false, "");
        logErrorRecord(contentId, ErrorType.NETWORKING, img.getUrl(), img.getName(), cause + "; HTTP statusCode=" + statusCode + "; message=" + message);
        // Handle cloudflare blocks
        if (request.getSite().isUseCloudflare() && 503 == statusCode && !isCloudFlareBlocked) {
            isCloudFlareBlocked = true; // prevent associated events & notifs to be fired more than once
            EventBus.getDefault().post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.STALE_CREDENTIALS));
            dao.clearDownloadParams(contentId);

            final String cfCookie = StringHelper.protect(HttpHelper.parseCookies(HttpHelper.getCookies(img.getUrl())).get(Consts.CLOUDFLARE_COOKIE));
            userActionNotificationManager.notify(new UserActionNotification(request.getSite(), cfCookie));

            if (HentoidApp.isInForeground())
                EventBus.getDefault().post(new DownloadReviveEvent(request.getSite(), cfCookie));
        }
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
        Chapter chp = img.getLinkedChapter();

        try {
            Optional<ImageFile> backupImg = parser.parseBackupUrl(backupUrl, requestHeaders, img.getOrder(), content.getQtyPages(), chp);
            processBackupImage(backupImg.orElse(null), img, dir, content);
        } catch (Exception e) {
            updateImageProperties(img, false, "");
            logErrorRecord(img.getContent().getTargetId(), ErrorType.NETWORKING, img.getUrl(), img.getName(), "Cannot process backup image : message=" + e.getMessage());
            Timber.e(e, "Error processing backup image.");
        }
    }

    private void processBackupImage(ImageFile backupImage,
                                    @NonNull ImageFile originalImage,
                                    @NonNull DocumentFile dir,
                                    Content content) throws ParseException {
        if (backupImage != null) {
            Timber.i("Backup URL contains image @ %s; queuing", backupImage.getUrl());
            originalImage.setUrl(backupImage.getUrl()); // Replace original image URL by backup image URL
            originalImage.setBackup(true); // Indicates the image is from a backup (for display in error logs)
            dao.insertImageFile(originalImage);
            requestQueueManager.queueRequest(buildImageDownloadRequest(originalImage, dir, content));
        } else {
            throw new ParseException("Failed to parse backup URL");
        }
    }

    /**
     * Download and unzip the given Ugoira to the given folder as an animated GIF file
     * NB : Ugoiuras are Pixiv's own animated pictures
     *
     * @param img  Link to the Ugoira file
     * @param dir  Folder to save the picture to
     * @param site Correponding site
     */
    private void downloadAndUnzipUgoira(
            @NonNull final ImageFile img,
            @NonNull final DocumentFile dir,
            @NonNull final Site site) {
        boolean isError = false;
        String errorMsg = "";

        File ugoiraCacheFolder = FileHelper.getOrCreateCacheFolder(getApplicationContext(), Consts.UGOIRA_CACHE_FOLDER + File.separator + img.getId());
        if (ugoiraCacheFolder != null) {
            String targetFileName = img.getName();
            try {
                // == Download archive
                ImmutablePair<Uri, String> result = DownloadHelper.downloadToFile(
                        site,
                        img.getUrl(),
                        img.getOrder(),
                        HttpHelper.webkitRequestHeadersToOkHttpHeaders(getRequestHeaders(img.getUrl(), img.getDownloadParams()), img.getUrl()),
                        Uri.fromFile(ugoiraCacheFolder),
                        targetFileName,
                        ArchiveHelper.ZIP_MIME_TYPE,
                        true,
                        downloadInterrupted,
                        null
                );

                // == Extract all frames
                ArchiveHelper.extractArchiveEntries(
                        getApplicationContext(),
                        result.left,
                        ugoiraCacheFolder,
                        null, // Extract everything; keep original names
                        downloadInterrupted,
                        null
                );

                // == Build the GIF using download params and extracted pics
                List<ImmutablePair<Uri, Integer>> frames = new ArrayList<>();

                // Get frame information
                Map<String, String> downloadParams = ContentHelper.parseDownloadParams(img.getDownloadParams());
                String ugoiraFramesStr = downloadParams.get(ContentHelper.KEY_DL_PARAMS_UGOIRA_FRAMES);
                List<Pair<String, Integer>> ugoiraFrames = JsonHelper.jsonToObject(ugoiraFramesStr, PixivIllustMetadata.UGOIRA_FRAMES_TYPE);

                // Map frame name to the downloaded file
                if (ugoiraFrames != null) {
                    for (Pair<String, Integer> frame : ugoiraFrames) {
                        File[] files = ugoiraCacheFolder.listFiles(pathname -> pathname.getName().endsWith(frame.first));
                        if (files != null && files.length > 0) {
                            frames.add(new ImmutablePair<>(Uri.fromFile(files[0]), frame.second));
                        }
                    }
                }

                // Assemble the GIF
                Uri ugoiraGifFile = ImageHelper.assembleGif(
                        getApplicationContext(),
                        ugoiraCacheFolder,
                        frames
                );

                // Save it to the book folder
                Uri finalImgUri = FileHelper.copyFile(
                        getApplicationContext(),
                        ugoiraGifFile,
                        dir.getUri(),
                        ImageHelper.MIME_IMAGE_GIF,
                        img.getName() + ".gif"
                );
                if (finalImgUri != null) {
                    img.setMimeType(ImageHelper.MIME_IMAGE_GIF);
                    img.setSize(FileHelper.fileSizeFromUri(getApplicationContext(), ugoiraGifFile));
                    updateImageProperties(img, true, finalImgUri.toString());
                } else
                    throw new IOException("Couldn't copy result ugoira file");
            } catch (Exception e) {
                Timber.w(e);
                isError = true;
                errorMsg = e.getMessage();
            } finally {
                if (!ugoiraCacheFolder.delete())
                    Timber.w("Couldn't delete ugoira folder %s", ugoiraCacheFolder.getAbsolutePath());
            }
            if (isError) {
                updateImageProperties(img, false, "");
                logErrorRecord(img.getContent().getTargetId(), ErrorType.IMG_PROCESSING, img.getUrl(), img.getName(), errorMsg);
            }
        }
    }

    /**
     * Update given image properties in DB
     *
     * @param img     Image to update
     * @param success True if download is successful; false if download failed
     */
    private void updateImageProperties(@NonNull ImageFile img, boolean success,
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
            case DownloadEvent.Type.EV_PAUSE:
                dao.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                requestQueueManager.cancelQueue();
                ContentQueueManager.getInstance().pauseQueue();
                notificationManager.cancel();
                break;
            case DownloadEvent.Type.EV_CANCEL:
                requestQueueManager.cancelQueue();
                downloadCanceled.set(true);
                downloadInterrupted.set(true);
                // Tracking Event (Download Canceled)
                HentoidApp.trackDownloadEvent("Cancelled");
                break;
            case DownloadEvent.Type.EV_SKIP:
                dao.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
                requestQueueManager.cancelQueue();
                downloadSkipped.set(true);
                downloadInterrupted.set(true);
                // Tracking Event (Download Skipped)
                HentoidApp.trackDownloadEvent("Skipped");
            case DownloadEvent.Type.EV_COMPLETE:
            case DownloadEvent.Type.EV_INTERRUPT_CONTENT:
            case DownloadEvent.Type.EV_PREPARATION:
            case DownloadEvent.Type.EV_PROGRESS:
            case DownloadEvent.Type.EV_UNPAUSE:
            default:
                // Other events aren't handled here
        }
    }

    /**
     * Get the HTTP request headers from the given download parameters for the given URL
     *
     * @param url               URL to use
     * @param downloadParamsStr Download parameters to extract the headers from
     * @return HTTP request headers
     */
    private Map<String, String> getRequestHeaders(@NonNull final String url, @NonNull final String downloadParamsStr) {
        Map<String, String> result = new HashMap<>();
        String cookieStr = null;
        Map<String, String> downloadParams = ContentHelper.parseDownloadParams(downloadParamsStr);
        if (!downloadParams.isEmpty()) {
            if (downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY))
                cookieStr = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY);
            if (downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY)) {
                String value = downloadParams.get(HttpHelper.HEADER_REFERER_KEY);
                if (value != null) result.put(HttpHelper.HEADER_REFERER_KEY, value);
            }
        }
        if (null == cookieStr) cookieStr = HttpHelper.getCookies(url);
        result.put(HttpHelper.HEADER_COOKIE_KEY, cookieStr);
        return result;
    }

    private void logErrorRecord(long contentId, ErrorType type, String url, String
            contentPart, String description) {
        ErrorRecord downloadRecord = new ErrorRecord(contentId, type, url, contentPart, description, Instant.now());
        if (contentId > 0) dao.insertErrorRecord(downloadRecord);
    }

    private void moveToErrors(long contentId) {
        Content content = dao.selectContent(contentId);
        if (null == content) return;

        content.setStatus(StatusContent.ERROR);
        content.setDownloadDate(Instant.now().toEpochMilli()); // Needs a download date to appear the right location when sorted by download date
        dao.insertContent(content);
        dao.deleteQueue(content);
        HentoidApp.trackDownloadEvent("Error");

        Context context = getApplicationContext();
        if (ContentHelper.updateQueueJson(context, dao))
            Timber.i(context.getString(R.string.queue_json_saved));
        else Timber.w(context.getString(R.string.queue_json_failed));

        notificationManager.notify(new DownloadErrorNotification(content));
    }

    private void applyRenamingRules(@NonNull Content content) {
        List<Attribute> newAttrs = new ArrayList<>();
        List<RenamingRule> rules = dao.selectRenamingRules(AttributeType.UNDEFINED, "");
        for (RenamingRule rule : rules) rule.computeParts();

        for (Attribute attr : content.getAttributes()) newAttrs.add(applyRenamingRule(attr, rules));

        content.putAttributes(newAttrs);
    }

    private Attribute applyRenamingRule(@NonNull Attribute attr, @NonNull List<RenamingRule> rules) {
        Attribute result = attr;
        for (RenamingRule rule : rules) {
            if (attr.getType().equals(rule.getAttributeType())) {
                String newName = processNewName(attr.getName(), rule);
                if (newName != null) {
                    result = new Attribute(attr.getType(), newName);
                    break;
                }
            }
        }
        return result;
    }

    @Nullable
    private String processNewName(@NonNull String attrName, @NonNull RenamingRule rule) {
        if (rule.doesMatchSourceName(attrName)) return rule.getTargetName(attrName);
        else return null;
    }
}
