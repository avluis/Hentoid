package me.devsaki.hentoid.workers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.util.Pair
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.CLOUDFLARE_COOKIE
import me.devsaki.hentoid.core.HentoidApp.Companion.isInForeground
import me.devsaki.hentoid.core.HentoidApp.Companion.trackDownloadEvent
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.core.UGOIRA_CACHE_FOLDER
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Content.DownloadMode
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.events.DownloadReviveEvent
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.json.sources.PixivIllustMetadata
import me.devsaki.hentoid.notification.download.DownloadErrorNotification
import me.devsaki.hentoid.notification.download.DownloadProgressNotification
import me.devsaki.hentoid.notification.download.DownloadSuccessNotification
import me.devsaki.hentoid.notification.download.DownloadWarningNotification
import me.devsaki.hentoid.notification.userAction.UserActionNotification
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.GroupHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.LogHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.download.ContentQueueManager
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueuePaused
import me.devsaki.hentoid.util.download.ContentQueueManager.pauseQueue
import me.devsaki.hentoid.util.download.DownloadHelper
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter.prefsSpeedCapToKbps
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter.setSpeedLimitKbps
import me.devsaki.hentoid.util.download.RequestOrder
import me.devsaki.hentoid.util.download.RequestOrder.NetworkError
import me.devsaki.hentoid.util.download.RequestQueueManager
import me.devsaki.hentoid.util.download.RequestQueueManager.Companion.getInstance
import me.devsaki.hentoid.util.exception.AccountException
import me.devsaki.hentoid.util.exception.CaptchaException
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.PreparationInterruptedException
import me.devsaki.hentoid.util.file.ArchiveHelper
import me.devsaki.hentoid.util.file.ArchiveHelper.extractArchiveEntries
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.FileHelper.MemoryUsageFigures
import me.devsaki.hentoid.util.image.ImageHelper
import me.devsaki.hentoid.util.image.ImageHelper.assembleGif
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator.addSampleNow
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator.getAvgSpeedKbps
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.NetworkHelper
import me.devsaki.hentoid.util.network.NetworkHelper.Connectivity
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.notification.NotificationManager
import org.apache.commons.lang3.tuple.ImmutablePair
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.security.InvalidParameterException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

class ContentDownloadWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.download_service, null) {

    companion object {
        // seconds; should be higher than the connect + I/O timeout defined in RequestQueueManager
        private const val IDLE_THRESHOLD = 20

        // KBps
        private const val LOW_NETWORK_THRESHOLD = 10

        fun isRunning(context: Context): Boolean {
            return isRunning(context, R.id.download_service)
        }
    }

    private enum class QueuingResult {
        CONTENT_FOUND, CONTENT_SKIPPED, CONTENT_FAILED, QUEUE_END
    }

    // DAO is full scope to avoid putting try / finally's everywhere and be sure to clear it upon worker stop
    private val dao: CollectionDAO

    // True if a Cancel event has been processed; false by default
    private val downloadCanceled = AtomicBoolean(false)

    // True if a Skip event has been processed; false by default
    private val downloadSkipped = AtomicBoolean(false)

    // downloadCanceled || downloadSkipped
    private val downloadInterrupted = AtomicBoolean(false)
    private var isCloudFlareBlocked = false

    private val userActionNotificationManager: NotificationManager
    private val requestQueueManager: RequestQueueManager

    init {
        EventBus.getDefault().register(this)
        dao = ObjectBoxDAO(context)
        requestQueueManager = getInstance(
            context, this::onRequestSuccess, this::onRequestError
        )
        userActionNotificationManager = NotificationManager(context, R.id.user_action_notification)
        setSpeedLimitKbps(prefsSpeedCapToKbps(Preferences.getDlSpeedCap()))
    }

    override fun getStartNotification(): BaseNotification {
        val message = applicationContext.resources.getString(R.string.starting_download)
        return DownloadProgressNotification(message, 0, 0, 0, 0, 0)
    }

    override fun onInterrupt() {
        requestQueueManager.cancelQueue()
        downloadCanceled.set(true)
        downloadInterrupted.set(true)
    }

    override fun onClear() {
        EventBus.getDefault().unregister(this)
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
        iterateQueue()
    }

    private fun iterateQueue() {
        // Process these here to avoid initializing notifications for downloads that will never start
        if (isQueuePaused) {
            Timber.i("Queue is paused. Download aborted.")
            return
        }
        var result = downloadFirstInQueue()
        while (result.left != QueuingResult.QUEUE_END) {
            if (result.left == QueuingResult.CONTENT_FOUND) watchProgress(result.right)
            result = downloadFirstInQueue()
        }
        notificationManager.cancel()
    }

    /**
     * Start the download of the 1st book of the download queue
     * NB : This method is not only called the 1st time the queue is awakened,
     * but also after every book has finished downloading
     *
     * @return Pair containing
     * - Left : Result of the processing
     * - Right : 1st book of the download queue
     */
    @SuppressLint("TimberExceptionLogging", "TimberArgCount")
    private fun downloadFirstInQueue(): ImmutablePair<QueuingResult, Content> {
        val contentPartImageList = "Image list"
        val context = applicationContext
        val log = LogHelper.LogInfo("download")

        // Check if queue has been paused
        if (isQueuePaused) {
            Timber.i("Queue is paused. Download aborted.")
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }
        @Connectivity val connectivity = NetworkHelper.getConnectivity(context)
        // Check for network connectivity
        if (Connectivity.NO_INTERNET == connectivity) {
            Timber.i("No internet connection available. Queue paused.")
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_INTERNET))
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }

        // Check for wifi if wifi-only mode is on
        if (Preferences.isQueueWifiOnly() && Connectivity.WIFI != connectivity) {
            Timber.i("No wi-fi connection available. Queue paused.")
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_WIFI))
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }


        // == Work on first item of queue

        // Check if there is a first item to process
        val queue = dao.selectQueue()
        if (queue.isEmpty()) {
            Timber.i("Queue is empty. Download aborted.")
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }

        // Look for the next unfrozen record
        var content: Content? = null
        var index = 0
        for (rec in queue) {
            if (!rec.isFrozen) {
                content = rec.content.reach(rec)
                if (content != null) break // Don't take broken links
            }
            index++
        }
        if (null == content) {
            Timber.i("No available downloads remaining. Queue paused.")
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_AVAILABLE_DOWNLOADS))
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }
        if (StatusContent.DOWNLOADED == content.status) {
            Timber.i("Content is already downloaded. Download aborted.")
            dao.deleteQueue(index)
            EventBus.getDefault()
                .post(DownloadEvent(content, DownloadEvent.Type.EV_COMPLETE, 0, 0, 0, 0))
            notificationManager.notify(DownloadErrorNotification(content))
            return ImmutablePair(QueuingResult.CONTENT_SKIPPED, null)
        }
        log.addEntry("Content OK : ${content.title}")
        EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.INIT, null))

        // Check for download folder existence, available free space and credentials
        var dir: DocumentFile? = null
        var location: StorageLocation = StorageLocation.NONE
        // Folder already set (e.g. resume paused download)
        if (content.storageUri.isNotEmpty()) {
            // Reset storage URI if unreachable (will be re-created later in the method)
            val rootFolder = FileHelper.getDocumentFromTreeUriString(context, content.storageUri)
            if (null == rootFolder) content.storageUri = "" else {
                val result = testFolder(context, content.storageUri)
                if (result != null) return result
                dir = FileHelper.getDocumentFromTreeUriString(
                    context,
                    content.storageUri
                ) // Will come out null if invalid
            }
        }
        // Auto-select location according to storage management strategy
        if (content.storageUri.isEmpty()) {
            location = DownloadHelper.selectDownloadLocation(context)
            val result = testFolder(context, Preferences.getStorageUri(location))
            if (result != null) return result
        }
        downloadCanceled.set(false)
        downloadSkipped.set(false)
        downloadInterrupted.set(false)
        isCloudFlareBlocked = false
        @DownloadMode val downloadMode = content.downloadMode
        dao.deleteErrorRecords(content.id)

        // == PREPARATION PHASE ==
        // Parse images from the site (using image list parser)
        //   - Case 1 : If no image is present => parse all images
        //   - Case 2 : If all images are in ERROR state => re-parse all images
        //   - Case 3 : If some images are in ERROR state and the site has backup URLs
        //     => re-parse images with ERROR state using their order as reference
        //   - Case 4 : If the book is merged and some chapters have zero images
        //     (equivalent to case 1 for chapters) => parse all images from these chapters
        EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.PROCESS_IMG, content))
        var images = content.imageList
        val targetImageStatus =
            if (downloadMode == DownloadMode.DOWNLOAD) StatusContent.SAVED else StatusContent.ONLINE

        var hasError = false
        val nbErrors = images.count { i -> i.status == StatusContent.ERROR }

        val isCase1 = images.isEmpty()
        val isCase2 = nbErrors > 0 && nbErrors == images.size
        val isCase3 = nbErrors > 0 && content.site.hasBackupURLs()
        val isCase4 =
            content.isManuallyMerged && content.chaptersList.any { c -> c.imageList.all { img -> img.status == StatusContent.ERROR } }

        log.addEntry("preparation OK $isCase1 $isCase2 $isCase3 $isCase4")
        LogHelper.writeLog(applicationContext, log)

        if (isCase1 || isCase2 || isCase3 || isCase4) {
            EventBus.getDefault()
                .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.FETCH_IMG, content))
            try {
                log.addEntry("parsing...")
                LogHelper.writeLog(applicationContext, log)
                images = parseMissingImages(
                    content,
                    images,
                    isCase1,
                    isCase2,
                    isCase3,
                    isCase4,
                    targetImageStatus, log
                )
            } catch (cpe: CaptchaException) {
                Timber.i(
                    cpe,
                    "A captcha has been found while parsing %s. Download aborted.",
                    content.title
                )
                logErrorRecord(
                    content.id,
                    ErrorType.CAPTCHA,
                    content.url,
                    contentPartImageList,
                    "Captcha found. Please go back to the site, browse a book and solve the captcha."
                )
                hasError = true
            } catch (ae: AccountException) {
                val description = String.format(
                    "Your %s account does not allow to download the book %s. %s. Download aborted.",
                    content.site.description,
                    content.title,
                    ae.message
                )
                Timber.i(ae, description)
                logErrorRecord(
                    content.id,
                    ErrorType.ACCOUNT,
                    content.url,
                    contentPartImageList,
                    description
                )
                hasError = true
            } catch (lre: LimitReachedException) {
                val description = String.format(
                    "The bandwidth limit has been reached while parsing %s. %s. Download aborted.",
                    content.title,
                    lre.message
                )
                Timber.i(lre, description)
                logErrorRecord(
                    content.id,
                    ErrorType.SITE_LIMIT,
                    content.url,
                    contentPartImageList,
                    description
                )
                hasError = true
            } catch (ie: PreparationInterruptedException) {
                Timber.i(ie, "Preparation of %s interrupted", content.title)
                downloadInterrupted.set(true)
                // not an error
            } catch (ere: EmptyResultException) {
                log.addEntry("EmptyResultException ${ere.message}")
                LogHelper.writeLog(applicationContext, log)
                Timber.i(
                    ere,
                    "No images have been found while parsing %s. Download aborted.",
                    content.title
                )
                logErrorRecord(
                    content.id,
                    ErrorType.PARSING,
                    content.url,
                    contentPartImageList,
                    "No images have been found. Error = " + ere.message
                )
                hasError = true
            } catch (e: Exception) {
                log.addEntry("Exception ${e.message}")
                LogHelper.writeLog(applicationContext, log)
                Timber.w(
                    e,
                    "An exception has occurred while parsing %s. Download aborted.",
                    content.title
                )
                logErrorRecord(
                    content.id,
                    ErrorType.PARSING,
                    content.url,
                    contentPartImageList,
                    e.message
                )
                hasError = true
            }
        } else if (nbErrors > 0) {
            // Other cases : Reset ERROR status of images to mark them as "to be downloaded" (in DB and in memory)
            log.addEntry("errors > 0")
            LogHelper.writeLog(applicationContext, log)
            dao.updateImageContentStatus(content.id, StatusContent.ERROR, targetImageStatus)
        } else {
            if (downloadMode == DownloadMode.STREAM) dao.updateImageContentStatus(
                content.id,
                null,
                StatusContent.ONLINE
            )
        }

        log.addEntry("Parsing done")
        LogHelper.writeLog(applicationContext, log)

        // Get updated Content with the udpated ID and status of new images
        content = dao.selectContent(content.id)
        if (null == content) return ImmutablePair(QueuingResult.CONTENT_SKIPPED, null)
        if (hasError) {
            moveToErrors(content.id)
            EventBus.getDefault()
                .post(DownloadEvent(content, DownloadEvent.Type.EV_COMPLETE, 0, 0, 0, 0))
            return ImmutablePair(QueuingResult.CONTENT_FAILED, content)
        }

        // In case the download has been canceled while in preparation phase
        // NB : No log of any sort because this is normal behaviour
        if (downloadInterrupted.get()) return ImmutablePair(QueuingResult.CONTENT_SKIPPED, null)
        EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.PREPARE_FOLDER, content))

        // Create destination folder for images to be downloaded
        if (null == dir) dir = ContentHelper.getOrCreateContentDownloadDir(
            applicationContext, content,
            location, false
        )
        // Folder creation failed
        if (null == dir || !dir.exists()) {
            val title = content.title
            val absolutePath = dir?.uri?.toString() ?: ""
            val message = String.format("Directory could not be created: %s.", absolutePath)
            Timber.w(message)
            logErrorRecord(content.id, ErrorType.IO, content.url, "Destination folder", message)
            notificationManager.notify(DownloadWarningNotification(title, absolutePath))

            // No sense in waiting for every image to be downloaded in error state (terrible waste of network resources)
            // => Create all images, flag them as failed as well as the book
            dao.updateImageContentStatus(content.id, targetImageStatus, StatusContent.ERROR)
            completeDownload(content.id, content.title, 0, images.size, 0)
            return ImmutablePair(QueuingResult.CONTENT_FAILED, content)
        }

        // Folder creation succeeds -> memorize its path
        val targetFolder: DocumentFile = dir
        content.storageUri = targetFolder.uri.toString()
        // Set QtyPages if the content parser couldn't do it (certain sources only)
        // Don't count the cover thumbnail in the number of pages
        if (0 == content.qtyPages) content.qtyPages = images.count { i -> i.isReadable }
        content.status = StatusContent.DOWNLOADING
        // Mark the cover for downloading when saving a streamed book
        if (downloadMode == DownloadMode.STREAM) content.cover.status =
            StatusContent.SAVED
        dao.insertContent(content)
        trackDownloadEvent("Added")
        Timber.i("Downloading '%s' [%s]", content.title, content.id)

        // Wait until the end of purge if the content is being purged (e.g. redownload from scratch)
        val isBeingDeleted = content.isBeingProcessed
        if (isBeingDeleted) EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.WAIT_PURGE, content))
        while (content!!.isBeingProcessed) {
            Timber.d("Waiting for purge to complete")
            content = dao.selectContent(content.id)
            if (null == content) return ImmutablePair(QueuingResult.CONTENT_SKIPPED, null)
            Helper.pause(1000)
            if (downloadInterrupted.get()) break
        }
        if (isBeingDeleted && !downloadInterrupted.get()) Timber.d("Purge completed; resuming download")


        // == DOWNLOAD PHASE ==
        EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.PREPARE_DOWNLOAD, content))

        // Set up downloader constraints
        if (content.site.parallelDownloadCap > 0 &&
            (requestQueueManager.downloadThreadCap > content.site.parallelDownloadCap
                    || -1 == requestQueueManager.downloadThreadCap)
        ) {
            Timber.d("Setting parallel downloads count to %s", content.site.parallelDownloadCap)
            requestQueueManager.initUsingDownloadThreadCount(
                applicationContext,
                content.site.parallelDownloadCap,
                true
            )
        }
        if (0 == content.site.parallelDownloadCap && requestQueueManager.downloadThreadCap > -1) {
            Timber.d("Resetting parallel downloads count to default")
            requestQueueManager.initUsingDownloadThreadCount(applicationContext, -1, true)
        }
        requestQueueManager.setNbRequestsPerSecond(content.site.requestsCapPerSecond)
        requestQueueManager.start()

        // In case the download has been canceled while in preparation phase
        // NB : No log of any sort because this is normal behaviour
        if (downloadInterrupted.get()) return ImmutablePair(QueuingResult.CONTENT_SKIPPED, null)
        val pagesToParse: MutableList<ImageFile> = ArrayList()
        val ugoirasToDownload: MutableList<ImageFile> = ArrayList()

        // Streamed download => just get the cover
        if (downloadMode == DownloadMode.STREAM) {
            val coverOptional = images.firstOrNull { img -> img.isCover }
            if (coverOptional != null) {
                enrichImageDownloadParams(coverOptional, content)
                requestQueueManager.queueRequest(
                    buildImageDownloadRequest(coverOptional, targetFolder, content)
                )
            }
        } else { // Regular download
            val covers = ArrayList<ImageFile>()
            // Queue image download requests
            images.forEachIndexed { idx, img ->
                if (img.status == StatusContent.SAVED) {
                    enrichImageDownloadParams(img, content)

                    // Set the next image of the list as a backup in case the cover URL is stale (might happen when restarting old downloads)
                    // NB : Per convention, cover is always the 1st picture of a given set
                    if (img.isCover) {
                        if (images.size > idx + 1) img.backupUrl = images[index + 1].url
                        covers.add(img)
                    }
                    if (img.needsPageParsing()) pagesToParse.add(img)
                    else if (img.downloadParams.contains(ContentHelper.KEY_DL_PARAMS_UGOIRA_FRAMES))
                        ugoirasToDownload.add(img)
                    else if (!img.isCover) requestQueueManager.queueRequest(
                        buildImageDownloadRequest(img, targetFolder, content)
                    )
                }
            }

            // Download cover last, to avoid being blocked by the server when downloading cover and page 1 back to back when they are the same resource
            covers.forEach {
                requestQueueManager.queueRequest(
                    buildImageDownloadRequest(it, targetFolder, content)
                )
            }

            // Parse pages for images
            if (pagesToParse.isNotEmpty()) {
                CoroutineScope(Dispatchers.Default).launch {
                    withContext(Dispatchers.IO) {
                        pagesToParse.forEach {
                            parsePageforImage(it, targetFolder, content)
                        }
                    }
                }
            }

            // Parse ugoiras for images
            if (ugoirasToDownload.isNotEmpty()) {
                CoroutineScope(Dispatchers.Default).launch {
                    withContext(Dispatchers.IO) {
                        ugoirasToDownload.forEach {
                            downloadAndUnzipUgoira(it, targetFolder, content.site)
                        }
                    }
                }
            }
        }
        EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.SAVE_QUEUE, content))
        if (ContentHelper.updateQueueJson(applicationContext, dao))
            Timber.i(context.getString(R.string.queue_json_saved))
        else Timber.w(context.getString(R.string.queue_json_failed))
        EventBus.getDefault()
            .post(DownloadEvent.fromPreparationStep(DownloadEvent.Step.START_DOWNLOAD, content))
        return ImmutablePair(QueuingResult.CONTENT_FOUND, content)
    }

    private fun parseMissingImages(
        content: Content,
        storedImages: List<ImageFile>,
        isCase1: Boolean,
        isCase2: Boolean,
        isCase3: Boolean,
        isCase4: Boolean,
        targetImageStatus: StatusContent,
        log: LogHelper.LogInfo?
    ): MutableList<ImageFile> {
        var result = storedImages.toMutableList()

        // Case 4 : Reparse chapters whose pages are all in ERROR state
        // NB : exclusive to the other cases
        if (isCase4) {
            log?.let {
                it.addEntry("Fetching chapters...")
                LogHelper.writeLog(applicationContext, it)
            }
            content.chaptersList.forEachIndexed { idx, ch ->
                if (ch.imageList.all { img -> img.status == StatusContent.ERROR }) {
                    /* There are two very different cases to consider
                     1- parse an actual chapter belonging to the overarching content (or at least originating from the same site)
                     2- parse a content merged inside the overarching content and stored as a chapter,
                        but originating from another site entirely
                     */
                    val chapterSite = Site.searchByUrl(ch.url)
                    if (null == chapterSite || !chapterSite.isVisible)
                        throw InvalidParameterException("A valid site couldn't be found from " + ch.url)

                    // We should parse a Content but all we have is a Chapter (merged book)
                    // Forge a bogus Content from the given chapter to retrieve images
                    val forgedContent = Content().setSite(chapterSite)
                    forgedContent.qtyPages = ch.imageList.size
                    forgedContent.setRawUrl(ch.url)
                    val onlineImages =
                        ContentHelper.fetchImageURLs(forgedContent, ch.url, targetImageStatus)

                    // Link the chapter to the found pages
                    for (img in onlineImages) img.chapterId = ch.id

                    // Remplace the image set within the results
                    val index = result.indexOfFirst { img -> img.chapterId == ch.id }
                    if (index > -1) {
                        result.removeIf { img -> img.chapterId == ch.id }
                        result.addAll(index, onlineImages)
                    }

                    // Link new image set to current chapter
                    content.chaptersList[idx].setImageFiles(onlineImages)
                }
            } // Chapters loop

            // Renumber all pages; fix covers if marked as ERROR
            var coverIndex = 0
            result.forEachIndexed { idx, img ->
                img.order = idx + 1
                if (img.isReadable) {
                    img.computeName(floor(log10(result.size.toDouble()) + 1).toInt())
                } else if (img.isCover) {
                    img.status = StatusContent.SAVED
                    if (0 == coverIndex++) {
                        img.url = content.coverImageUrl
                    } else {
                        img.name = THUMB_FILE_NAME + coverIndex
                    }
                }
            }

            // Manually insert updated chapters
            dao.insertChapters(content.chaptersList)
        } else if (isCase1 || isCase2 || isCase3) {
            log?.let {
                it.addEntry("Fetching images from ${content.galleryUrl}...")
                LogHelper.writeLog(applicationContext, it)
            }
            val onlineImages =
                ContentHelper.fetchImageURLs(content, content.galleryUrl, targetImageStatus)
            log?.let {
                it.addEntry("Images fetched : ${onlineImages.size}")
                LogHelper.writeLog(applicationContext, it)
            }
            // Cases 1 and 2 : Replace existing images with the parsed images
            if (isCase1 || isCase2) result = onlineImages
            // Case 3 : Replace images in ERROR state with the parsed images at the same position
            if (isCase3) {
                for (i in result.indices) {
                    val oldImage = result[i]
                    if (oldImage.status == StatusContent.ERROR) {
                        onlineImages.forEach { newImg ->
                            if (newImg.order == oldImage.order) result[i] = newImg
                        }
                    }
                }
            }
        }

        content.qtyPages = 0
        dao.insertContent(content)

        // Manually insert new images (without using insertContent)
        dao.replaceImageList(content.id, result)
        return result
    }

    private fun enrichImageDownloadParams(img: ImageFile, content: Content) {
        // Enrich download params just in case
        val downloadParams: MutableMap<String, String> =
            if (img.downloadParams.length > 2) ContentHelper.parseDownloadParams(img.downloadParams) else HashMap()
        // Add referer if unset
        if (!downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY)) downloadParams[HttpHelper.HEADER_REFERER_KEY] =
            content.galleryUrl
        // Add cookies if unset or if the site needs fresh cookies
        if (!downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY) || content.site.isUseCloudflare) downloadParams[HttpHelper.HEADER_COOKIE_KEY] =
            HttpHelper.getCookies(img.url)
        img.downloadParams =
            JsonHelper.serializeToJson<Map<String, String>>(
                downloadParams,
                JsonHelper.MAP_STRINGS
            )
    }

    /**
     * Watch download progress
     *
     *
     * NB : download pause is managed at the Request queue level (see RequestQueueManager.pauseQueue / startQueue)
     *
     * @param content Content to watch (1st book of the download queue)
     */
    private fun watchProgress(content: Content) {
        val refreshDelayMs = 500
        var isDone: Boolean
        var pagesOK = 0
        var pagesKO = 0
        var downloadedBytes: Long = 0
        var firstPageDownloaded = false
        var deltaPages = 0
        var nbDeltaZeroPages = 0
        var networkBytes: Long = 0
        var deltaNetworkBytes: Long
        var nbDeltaLowNetwork = 0
        val images = content.imageList
        // Compute total downloadable pages; online (stream) pages do not count
        val totalPages = images.count { i: ImageFile -> i.status != StatusContent.ONLINE }
        val contentQueueManager = ContentQueueManager
        do {
            val statuses = dao.countProcessedImagesById(content.id)
            var status = statuses[StatusContent.DOWNLOADED]

            // Measure idle time since last iteration
            if (status != null) {
                deltaPages = status.left - pagesOK
                if (deltaPages == 0) nbDeltaZeroPages++ else {
                    firstPageDownloaded = true
                    nbDeltaZeroPages = 0
                }
                pagesOK = status.left
                downloadedBytes = status.right
            }
            status = statuses[StatusContent.ERROR]
            if (status != null) pagesKO = status.left
            val downloadedMB = downloadedBytes / (1024.0 * 1024)
            val progress = pagesOK + pagesKO
            isDone = progress == totalPages
            Timber.d(
                "Progress: OK:%d size:%dMB - KO:%d - Total:%d",
                pagesOK,
                downloadedMB.toInt(),
                pagesKO,
                totalPages
            )

            // Download speed and size estimation
            val networkBytesNow = NetworkHelper.getIncomingNetworkUsage(applicationContext)
            deltaNetworkBytes = networkBytesNow - networkBytes
            if (deltaNetworkBytes < 1024 * LOW_NETWORK_THRESHOLD * refreshDelayMs / 1000f && firstPageDownloaded) nbDeltaLowNetwork++ // LOW_NETWORK_THRESHOLD KBps threshold once download has started
            else nbDeltaLowNetwork = 0
            networkBytes = networkBytesNow
            addSampleNow(networkBytes)
            val avgSpeedKbps = getAvgSpeedKbps().toInt()
            Timber.d(
                "deltaPages: %d / deltaNetworkBytes: %s",
                deltaPages,
                FileHelper.formatHumanReadableSize(
                    deltaNetworkBytes,
                    applicationContext.resources
                )
            )
            Timber.d(
                "nbDeltaZeroPages: %d / nbDeltaLowNetwork: %d",
                nbDeltaZeroPages,
                nbDeltaLowNetwork
            )

            // Restart request queue when the queue has idled for too long
            // Idle = very low download speed _AND_ no new pages downloaded
            if (nbDeltaLowNetwork > IDLE_THRESHOLD * 1000f / refreshDelayMs && nbDeltaZeroPages > IDLE_THRESHOLD * 1000f / refreshDelayMs) {
                nbDeltaLowNetwork = 0
                nbDeltaZeroPages = 0
                Timber.d("Inactivity detected ====> restarting request queue")
                requestQueueManager.resetRequestQueue(false)
            }
            var estimateBookSizeMB = -1.0
            if (pagesOK > 3 && progress > 0 && totalPages > 0) {
                estimateBookSizeMB = downloadedMB / (progress * 1.0 / totalPages)
                Timber.v(
                    "Estimate book size calculated for wifi check : %s MB",
                    estimateBookSizeMB
                )
            }
            notificationManager.notify(
                DownloadProgressNotification(
                    content.title,
                    progress,
                    totalPages,
                    downloadedMB.toInt(),
                    estimateBookSizeMB.toInt(),
                    avgSpeedKbps
                )
            )
            EventBus.getDefault().post(
                DownloadEvent(
                    content,
                    DownloadEvent.Type.EV_PROGRESS,
                    pagesOK,
                    pagesKO,
                    totalPages,
                    downloadedBytes
                )
            )

            // If the "skip large downloads on mobile data" is on, skip if needed
            if (Preferences.isDownloadLargeOnlyWifi() &&
                (estimateBookSizeMB > Preferences.getDownloadLargeOnlyWifiThresholdMB()
                        || totalPages > Preferences.getDownloadLargeOnlyWifiThresholdPages())
            ) {
                @Connectivity val connectivity = NetworkHelper.getConnectivity(
                    applicationContext
                )
                if (Connectivity.WIFI != connectivity) {
                    // Move the book to the errors queue and signal it as skipped
                    logErrorRecord(content.id, ErrorType.WIFI, content.url, "Book", "")
                    moveToErrors(content.id)
                    EventBus.getDefault()
                        .post(DownloadCommandEvent(DownloadCommandEvent.Type.EV_SKIP))
                }
            }

            // We're polling the DB because we can't observe LiveData from a background service
            Helper.pause(refreshDelayMs)
        } while (!isDone && !downloadInterrupted.get() && !contentQueueManager.isQueuePaused)
        if (isDone && !downloadInterrupted.get()) {
            // NB : no need to supply the Content itself as it has not been updated during the loop
            completeDownload(content.id, content.title, pagesOK, pagesKO, downloadedBytes)
        } else {
            Timber.d("Content download paused : %s [%s]", content.title, content.id)
            if (downloadCanceled.get()) notificationManager.cancel()
        }
    }

    /**
     * Completes the download of a book when all images have been processed
     * Then launches a new IntentService
     *
     * @param contentId Id of the Content to mark as downloaded
     */
    private fun completeDownload(
        contentId: Long, title: String,
        pagesOK: Int, pagesKO: Int, sizeDownloadedBytes: Long
    ) {
        val contentQueueManager = ContentQueueManager
        // Get the latest value of Content
        val content = dao.selectContent(contentId)
        if (null == content) {
            Timber.w("Content ID %s not found", contentId)
            return
        }
        EventBus.getDefault()
            .post(
                DownloadEvent.fromPreparationStep(
                    DownloadEvent.Step.COMPLETE_DOWNLOAD,
                    content
                )
            )
        if (!downloadInterrupted.get()) {
            var images: List<ImageFile>? = content.imageFiles
            if (null == images) images = emptyList()
            val nbImages = images.count { i: ImageFile -> !i.isCover } // Don't count the cover
            var hasError = false
            // Set error state if no image has been detected at all
            if (0 == content.qtyPages && 0 == nbImages) {
                logErrorRecord(
                    contentId,
                    ErrorType.PARSING,
                    content.galleryUrl,
                    "pages",
                    "The book has no pages"
                )
                hasError = true
            }
            // Set error state if less pages than initially detected - More than 10% difference in number of pages
            if (content.qtyPages > 0 && nbImages < content.qtyPages && abs(nbImages - content.qtyPages) > content.qtyPages * 0.1) {
                val errorMsg = String.format(
                    "The number of images found (%s) does not match the book's number of pages (%s)",
                    nbImages,
                    content.qtyPages
                )
                logErrorRecord(
                    contentId,
                    ErrorType.PARSING,
                    content.galleryUrl,
                    "pages",
                    errorMsg
                )
                hasError = true
            }
            // Set error state if there are non-downloaded pages
            // NB : this should not happen theoretically
            val nbDownloadedPages = content.nbDownloadedPages
            if (nbDownloadedPages < content.qtyPages) {
                val errorMsg = String.format(
                    "The number of downloaded images (%s) does not match the book's number of pages (%s)",
                    nbDownloadedPages,
                    content.qtyPages
                )
                logErrorRecord(
                    contentId,
                    ErrorType.PARSING,
                    content.galleryUrl,
                    "pages",
                    errorMsg
                )
                hasError = true
            }

            // If additional pages have been downloaded (e.g. new chapters on existing book),
            // update the book's number of pages and download date
            val now = Instant.now().toEpochMilli()
            if (nbImages > content.qtyPages) {
                content.qtyPages = nbImages
                content.downloadDate = now
            }
            if (content.storageUri.isEmpty()) return
            val dir = FileHelper.getDocumentFromTreeUriString(
                applicationContext, content.storageUri
            )
            if (dir != null) {
                // Auto-retry when error pages are remaining and conditions are met
                // NB : Differences between expected and detected pages (see block above) can't be solved by retrying - it's a parsing issue
                if (pagesKO > 0 && Preferences.isDlRetriesActive() && content.numberDownloadRetries < Preferences.getDlRetriesNumber()) {
                    val freeSpaceRatio =
                        MemoryUsageFigures(applicationContext, dir).freeUsageRatio100
                    if (freeSpaceRatio < Preferences.getDlRetriesMemLimit()) {
                        Timber.i(
                            "Initiating auto-retry #%s for content %s (%s%% free space)",
                            content.numberDownloadRetries + 1,
                            content.title,
                            freeSpaceRatio
                        )
                        logErrorRecord(
                            content.id,
                            ErrorType.UNDEFINED,
                            "",
                            content.title,
                            "Auto-retry #" + content.numberDownloadRetries
                        )
                        content.increaseNumberDownloadRetries()

                        // Re-queue all failed images
                        for (img in images) if (img.status == StatusContent.ERROR) {
                            Timber.i(
                                "Auto-retry #%s for content %s / image @ %s",
                                content.numberDownloadRetries,
                                content.title,
                                img.url
                            )
                            img.status = StatusContent.SAVED
                            dao.insertImageFile(img)
                            requestQueueManager.queueRequest(
                                buildImageDownloadRequest(
                                    img,
                                    dir,
                                    content
                                )
                            )
                        }
                        return
                    }
                }

                // Compute perceptual hash for the cover picture
                ContentHelper.computeAndSaveCoverHash(applicationContext, content, dao)

                // Mark content as downloaded (download processing date; if none set before)
                if (0L == content.downloadDate) content.downloadDate = now
                if (0 == pagesKO && !hasError) {
                    content.downloadParams = ""
                    content.downloadCompletionDate = now
                    content.lastEditDate = now
                    content.status = StatusContent.DOWNLOADED
                    applyRenamingRules(content)
                } else {
                    content.status = StatusContent.ERROR
                }
                content.computeSize()

                // Replace the book with its new title, if any
                if (content.replacementTitle.isNotEmpty()) {
                    content.title = content.replacementTitle
                    content.replacementTitle = ""
                }

                // Save JSON file
                try {
                    val jsonFile = JsonHelper.jsonToFile(
                        applicationContext, JsonContent.fromEntity(content),
                        JsonContent::class.java, dir, JSON_FILE_NAME_V2
                    )
                    // Cache its URI to the newly created content
                    if (jsonFile != null) {
                        content.jsonUri = jsonFile.uri.toString()
                    } else {
                        Timber.w("JSON file could not be cached for %s", title)
                    }
                } catch (e: IOException) {
                    Timber.e(e, "I/O Error saving JSON: %s", title)
                }
                ContentHelper.addContent(applicationContext, dao, content)

                // Delete the duplicate book that was meant to be replaced, if any
                if (!content.contentToReplace.isNull) {
                    val contentToReplace = content.contentToReplace.target
                    if (contentToReplace != null) {
                        // Keep that content's custom group and order if needed
                        val groupItems = contentToReplace.getGroupItems(Grouping.CUSTOM)
                        if (groupItems.isNotEmpty()) {
                            for (gi in groupItems) {
                                GroupHelper.moveContentToCustomGroup(
                                    content, gi.group.target, gi.order,
                                    dao
                                )
                            }
                        }
                        EventBus.getDefault().post(
                            DownloadEvent.fromPreparationStep(
                                DownloadEvent.Step.REMOVE_DUPLICATE,
                                content
                            )
                        )
                        try {
                            ContentHelper.removeContent(
                                applicationContext,
                                dao, contentToReplace
                            )
                        } catch (e: ContentNotProcessedException) {
                            Timber.w(e)
                        }
                    }
                }
                Timber.i("Content download finished: %s [%s]", title, contentId)

                // Delete book from queue
                dao.deleteQueue(content)

                // Increase downloads count
                contentQueueManager.downloadComplete()
                if (0 == pagesKO) {
                    val downloadCount = contentQueueManager.downloadCount
                    notificationManager.notify(DownloadSuccessNotification(downloadCount))

                    // Tracking Event (Download Success)
                    trackDownloadEvent("Success")
                } else {
                    notificationManager.notify(DownloadErrorNotification(content))

                    // Tracking Event (Download Error)
                    trackDownloadEvent("Error")
                }

                // Signals current download as completed
                Timber.d("CompleteActivity : OK = %s; KO = %s", pagesOK, pagesKO)
                EventBus.getDefault().post(
                    DownloadEvent(
                        content,
                        DownloadEvent.Type.EV_COMPLETE,
                        pagesOK,
                        pagesKO,
                        nbImages,
                        sizeDownloadedBytes
                    )
                )
                val context = applicationContext
                if (
                    ContentHelper.updateQueueJson(context, dao)
                ) Timber.i(context.getString(R.string.queue_json_saved)) else Timber.w(
                    context.getString(
                        R.string.queue_json_failed
                    )
                )

                AchievementsManager.checkStorage(context)
                AchievementsManager.checkCollection(context)

                // Tracking Event (Download Completed)
                trackDownloadEvent("Completed")
            } else {
                Timber.w(
                    "completeDownload : Directory %s does not exist - JSON not saved",
                    content.storageUri
                )
            }
        } else if (downloadCanceled.get()) {
            Timber.d("Content download canceled: %s [%s]", title, contentId)
            notificationManager.cancel()
        } else {
            Timber.d("Content download skipped : %s [%s]", title, contentId)
        }
    }

    /**
     * Parse the given ImageFile's page URL for the image and save it to the given folder
     *
     * @param img     Image to parse
     * @param dir     Folder to save the resulting image to
     * @param content Correponding content
     */
    @SuppressLint("TimberArgCount")
    private fun parsePageforImage(
        img: ImageFile,
        dir: DocumentFile,
        content: Content
    ) {
        val site = content.site
        val pageUrl = HttpHelper.fixUrl(img.pageUrl, site.url)

        // Apply image download parameters
        val requestHeaders = getRequestHeaders(pageUrl, img.downloadParams)
        try {
            val reqHeaders =
                HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, img.pageUrl)
            val parser = ContentParserFactory.getImageListParser(content.site)
            val pages = parser.parseImagePage(img.pageUrl, reqHeaders)
            img.url = pages.first
            // Set backup URL
            if (pages.second != null) img.backupUrl = pages.second
            // Queue the picture
            requestQueueManager.queueRequest(buildImageDownloadRequest(img, dir, content))
        } catch (e: UnsupportedOperationException) {
            Timber.i(e, "Could not read image from page %s", img.pageUrl)
            updateImageProperties(img, false, "")
            logErrorRecord(
                content.id,
                ErrorType.PARSING,
                img.pageUrl,
                "Page " + img.name,
                "Could not read image from page " + img.pageUrl + " " + e.message
            )
        } catch (e: IllegalArgumentException) {
            Timber.i(e, "Could not read image from page %s", img.pageUrl)
            updateImageProperties(img, false, "")
            logErrorRecord(
                content.id,
                ErrorType.PARSING,
                img.pageUrl,
                "Page " + img.name,
                "Could not read image from page " + img.pageUrl + " " + e.message
            )
        } catch (ioe: IOException) {
            Timber.i(ioe, "Could not read page data from %s", img.pageUrl)
            updateImageProperties(img, false, "")
            logErrorRecord(
                content.id,
                ErrorType.IO,
                img.pageUrl,
                "Page " + img.name,
                "Could not read page data from " + img.pageUrl + " " + ioe.message
            )
        } catch (lre: LimitReachedException) {
            val description = String.format(
                "The bandwidth limit has been reached while parsing %s. %s. Download aborted.",
                content.title,
                lre.message
            )
            Timber.i(lre, description)
            updateImageProperties(img, false, "")
            logErrorRecord(
                content.id,
                ErrorType.SITE_LIMIT,
                content.url,
                "Page " + img.name,
                description
            )
        } catch (ere: EmptyResultException) {
            Timber.i(ere, "No images have been found while parsing %s", content.title)
            updateImageProperties(img, false, "")
            logErrorRecord(
                content.id,
                ErrorType.PARSING,
                img.pageUrl,
                "Page " + img.name,
                "No images have been found. Error = " + ere.message
            )
        } catch (e: Exception) { // Where "the rest" is caught
            Timber.i(e, "An unexpected error occured while parsing %s", content.title)
            updateImageProperties(img, false, "")
            logErrorRecord(
                content.id,
                ErrorType.UNDEFINED,
                img.pageUrl,
                "Page " + img.name,
                "No images have been found. Error = " + e.message
            )
        }
    }

    private fun buildImageDownloadRequest(
        img: ImageFile,
        dir: DocumentFile,
        content: Content
    ): RequestOrder {
        val site = content.site
        val imageUrl = HttpHelper.fixUrl(img.url, site.url)

        // Apply image download parameters
        val requestHeaders = getRequestHeaders(imageUrl, img.downloadParams)

        // Process backup URLs if any
        if (img.backupUrl.isEmpty() && content.site.hasBackupURLs()) {
            val imgParser = ContentParserFactory.getImageListParser(content)
            img.backupUrl = imgParser.getAltUrl(imageUrl)
        }
        val backupUrlFinal = HttpHelper.fixUrl(img.backupUrl, site.url)

        // Create request order
        return RequestOrder(
            RequestOrder.HttpMethod.GET,
            imageUrl,
            requestHeaders,
            site,
            dir,
            img.name,
            img.order,
            backupUrlFinal,
            img
        )
    }

    // This is run on the I/O thread pool spawned by the downloader
    private fun onRequestSuccess(request: RequestOrder, fileUri: Uri) {
        val img = request.img
        val imgFile = FileHelper.getFileFromSingleUriString(
            applicationContext, fileUri.toString()
        )
        if (imgFile != null) {
            img.size = imgFile.length()
            img.mimeType = imgFile.type
            updateImageProperties(img, true, fileUri.toString())
        } else {
            Timber.i(
                "I/O error - Image %s not saved in dir %s",
                img.url,
                request.targetDir.uri.path
            )
            updateImageProperties(img, false, "")
            logErrorRecord(
                img.content.targetId,
                ErrorType.IO,
                img.url,
                "Picture " + img.name,
                "Save failed in dir " + request.targetDir.uri.path
            )
        }
    }

    // This is run on the I/O thread pool spawned by the downloader
    private fun onRequestError(request: RequestOrder, error: NetworkError) {
        val img = request.img
        val contentId = img.contentId

        // Try with the backup URL, if it exists and if the current image isn't a backup itself
        if (!img.isBackup && request.backupUrl.isNotEmpty()) {
            tryUsingBackupUrl(img, request.targetDir, request.backupUrl, request.headers)
            return
        }

        // If no backup, then process the error
        val statusCode = error.statusCode
        val message = error.message + if (img.isBackup) " (from backup URL)" else ""
        var cause = "Network error"
        if (error.type === RequestOrder.NetworkErrorType.FILE_IO) cause =
            "File I/O" else if (error.type === RequestOrder.NetworkErrorType.PARSE) cause =
            "Parsing"
        Timber.d("$message $cause")
        updateImageProperties(img, false, "")
        logErrorRecord(
            contentId, ErrorType.NETWORKING, img.url, img.name,
            "$cause; HTTP statusCode=$statusCode; message=$message"
        )
        // Handle cloudflare blocks
        if (request.site.isUseCloudflare && 503 == statusCode && !isCloudFlareBlocked) {
            isCloudFlareBlocked =
                true // prevent associated events & notifs to be fired more than once
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.STALE_CREDENTIALS))
            dao.clearDownloadParams(contentId)
            val cfCookie =
                StringHelper.protect(HttpHelper.parseCookies(HttpHelper.getCookies(img.url))[CLOUDFLARE_COOKIE])
            userActionNotificationManager.notify(UserActionNotification(request.site, cfCookie))
            if (isInForeground()) EventBus.getDefault()
                .post(DownloadReviveEvent(request.site, cfCookie))
        }
    }

    private fun tryUsingBackupUrl(
        img: ImageFile,
        dir: DocumentFile,
        backupUrl: String,
        requestHeaders: Map<String, String>
    ) {
        Timber.i("Using backup URL %s", backupUrl)
        val content = img.content.target ?: return
        val site = content.site
        val parser = ContentParserFactory.getImageListParser(site)
        val chp = img.linkedChapter
        try {
            val backupImg =
                parser.parseBackupUrl(
                    backupUrl,
                    requestHeaders,
                    img.order,
                    content.qtyPages,
                    chp
                )
            processBackupImage(backupImg, img, dir, content)
        } catch (e: Exception) {
            updateImageProperties(img, false, "")
            logErrorRecord(
                img.content.targetId,
                ErrorType.NETWORKING,
                img.url,
                img.name,
                "Cannot process backup image : message=" + e.message
            )
            Timber.e(e, "Error processing backup image.")
        }
    }

    @Throws(ParseException::class)
    private fun processBackupImage(
        backupImage: ImageFile?,
        originalImage: ImageFile,
        dir: DocumentFile,
        content: Content
    ) {
        if (backupImage != null) {
            Timber.i("Backup URL contains image @ %s; queuing", backupImage.url)
            originalImage.url =
                backupImage.url // Replace original image URL by backup image URL
            originalImage.isBackup =
                true // Indicates the image is from a backup (for display in error logs)
            dao.insertImageFile(originalImage)
            requestQueueManager.queueRequest(
                buildImageDownloadRequest(
                    originalImage,
                    dir,
                    content
                )
            )
        } else {
            throw ParseException("Failed to parse backup URL")
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
    private fun downloadAndUnzipUgoira(
        img: ImageFile,
        dir: DocumentFile,
        site: Site
    ) {
        var isError = false
        var errorMsg = ""
        val ugoiraCacheFolder = FileHelper.getOrCreateCacheFolder(
            applicationContext, UGOIRA_CACHE_FOLDER + File.separator + img.id
        )
        if (ugoiraCacheFolder != null) {
            val targetFileName = img.name
            try {
                // == Download archive
                val result = DownloadHelper.downloadToFile(
                    applicationContext,
                    site,
                    img.url,
                    HttpHelper.webkitRequestHeadersToOkHttpHeaders(
                        getRequestHeaders(
                            img.url,
                            img.downloadParams
                        ), img.url
                    ),
                    Uri.fromFile(ugoiraCacheFolder),
                    targetFileName,
                    downloadInterrupted,
                    ArchiveHelper.ZIP_MIME_TYPE,
                    resourceId = img.order
                )

                val targetFileUri = result.first
                targetFileUri
                    ?: throw IOException("Couldn't download ugoira file : resource not available")

                // == Extract all frames
                extractArchiveEntries(
                    applicationContext,
                    targetFileUri,
                    ugoiraCacheFolder,
                    null,  // Extract everything; keep original names
                    downloadInterrupted
                )

                // == Build the GIF using download params and extracted pics
                val frames: MutableList<ImmutablePair<Uri, Int>> = ArrayList()

                // Get frame information
                val downloadParams = ContentHelper.parseDownloadParams(img.downloadParams)
                val ugoiraFramesStr = downloadParams[ContentHelper.KEY_DL_PARAMS_UGOIRA_FRAMES]
                val ugoiraFrames = JsonHelper.jsonToObject<List<Pair<String, Int>>>(
                    ugoiraFramesStr,
                    PixivIllustMetadata.UGOIRA_FRAMES_TYPE
                )

                // Map frame name to the downloaded file
                if (ugoiraFrames != null) {
                    for (frame in ugoiraFrames) {
                        val files = ugoiraCacheFolder.listFiles(FileFilter { pathname: File ->
                            pathname.name.endsWith(frame.first)
                        })
                        if (files != null && files.isNotEmpty()) {
                            frames.add(
                                ImmutablePair(
                                    Uri.fromFile(
                                        files[0]
                                    ), frame.second
                                )
                            )
                        }
                    }
                }

                // Assemble the GIF
                val ugoiraGifFile = assembleGif(
                    applicationContext,
                    ugoiraCacheFolder,
                    frames
                ) ?: throw IOException("Couldn't assemble ugoira file")

                // Save it to the book folder
                val finalImgUri = FileHelper.copyFile(
                    applicationContext,
                    ugoiraGifFile,
                    dir.uri,
                    ImageHelper.MIME_IMAGE_GIF,
                    img.name + ".gif"
                ) ?: throw IOException("Couldn't copy result ugoira file")

                img.mimeType = ImageHelper.MIME_IMAGE_GIF
                img.size = FileHelper.fileSizeFromUri(
                    applicationContext,
                    ugoiraGifFile
                )
                updateImageProperties(img, true, finalImgUri.toString())
            } catch (e: Exception) {
                Timber.w(e)
                isError = true
                errorMsg = StringHelper.protect(e.message)
            } finally {
                if (!ugoiraCacheFolder.delete()) Timber.w(
                    "Couldn't delete ugoira folder %s",
                    ugoiraCacheFolder.absolutePath
                )
            }
            if (isError) {
                updateImageProperties(img, false, "")
                logErrorRecord(
                    img.content.targetId,
                    ErrorType.IMG_PROCESSING,
                    img.url,
                    img.name,
                    errorMsg
                )
            }
        }
    }

    /**
     * Update given image properties in DB
     *
     * @param img     Image to update
     * @param success True if download is successful; false if download failed
     */
    private fun updateImageProperties(
        img: ImageFile, success: Boolean,
        uriStr: String
    ) {
        img.status = if (success) StatusContent.DOWNLOADED else StatusContent.ERROR
        img.fileUri = uriStr
        if (success) img.downloadParams = ""
        if (img.id > 0) dao.updateImageFileStatusParamsMimeTypeUriSize(img) // because thumb image isn't in the DB
    }

    /**
     * Download event handler called by the event bus
     *
     * @param event Download event
     */
    @Subscribe
    fun onDownloadCommand(event: DownloadCommandEvent) {
        when (event.type) {
            DownloadCommandEvent.Type.EV_PAUSE -> {
                dao.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED)
                requestQueueManager.cancelQueue()
                pauseQueue()
                notificationManager.cancel()
            }

            DownloadCommandEvent.Type.EV_CANCEL -> {
                requestQueueManager.cancelQueue()
                downloadCanceled.set(true)
                downloadInterrupted.set(true)
                // Tracking Event (Download Canceled)
                trackDownloadEvent("Cancelled")
            }

            DownloadCommandEvent.Type.EV_SKIP -> {
                dao.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED)
                requestQueueManager.cancelQueue()
                downloadSkipped.set(true)
                downloadInterrupted.set(true)
                // Tracking Event (Download Skipped)
                trackDownloadEvent("Skipped")
            }

            DownloadCommandEvent.Type.EV_INTERRUPT_CONTENT, DownloadCommandEvent.Type.EV_UNPAUSE -> {}
        }
        EventBus.getDefault().post(DownloadEvent(event))
    }

    /**
     * Get the HTTP request headers from the given download parameters for the given URL
     *
     * @param url               URL to use
     * @param downloadParamsStr Download parameters to extract the headers from
     * @return HTTP request headers
     */
    private fun getRequestHeaders(
        url: String,
        downloadParamsStr: String
    ): Map<String, String> {
        val result: MutableMap<String, String> = HashMap()
        var cookieStr = ""
        val downloadParams = ContentHelper.parseDownloadParams(downloadParamsStr)
        if (downloadParams.isNotEmpty()) {
            if (downloadParams.containsKey(HttpHelper.HEADER_COOKIE_KEY)) {
                val value = downloadParams[HttpHelper.HEADER_COOKIE_KEY]
                if (value != null) cookieStr = value
            }
            if (downloadParams.containsKey(HttpHelper.HEADER_REFERER_KEY)) {
                val value = downloadParams[HttpHelper.HEADER_REFERER_KEY]
                if (value != null) result[HttpHelper.HEADER_REFERER_KEY] = value
            }
        }
        if (cookieStr.isEmpty()) cookieStr = HttpHelper.getCookies(url)
        result[HttpHelper.HEADER_COOKIE_KEY] = cookieStr
        return result
    }

    private fun logErrorRecord(
        contentId: Long,
        type: ErrorType,
        url: String,
        contentPart: String,
        description: String?
    ) {
        val downloadRecord =
            ErrorRecord(contentId, type, url, contentPart, description, Instant.now())
        if (contentId > 0) dao.insertErrorRecord(downloadRecord)
    }

    private fun moveToErrors(contentId: Long) {
        val content = dao.selectContent(contentId) ?: return
        content.status = StatusContent.ERROR
        content.downloadDate = Instant.now()
            .toEpochMilli() // Needs a download date to appear the right location when sorted by download date
        dao.insertContent(content)
        dao.deleteQueue(content)
        trackDownloadEvent("Error")
        val context = applicationContext
        if (ContentHelper.updateQueueJson(context, dao)
        ) Timber.i(context.getString(R.string.queue_json_saved)) else Timber.w(
            context.getString(
                R.string.queue_json_failed
            )
        )
        notificationManager.notify(DownloadErrorNotification(content))
    }

    private fun applyRenamingRules(content: Content) {
        val newAttrs: MutableList<Attribute> = ArrayList()
        val rules = dao.selectRenamingRules(AttributeType.UNDEFINED, "")
        for (rule in rules) rule.computeParts()
        for (attr in content.attributes) newAttrs.add(applyRenamingRule(attr, rules))
        content.putAttributes(newAttrs)
    }

    private fun applyRenamingRule(attr: Attribute, rules: List<RenamingRule>): Attribute {
        var result = attr
        for (rule in rules) {
            if (attr.type == rule.attributeType) {
                val newName = processNewName(attr.name, rule)
                if (newName != null) {
                    result = Attribute(attr.type, newName)
                    break
                }
            }
        }
        return result
    }

    private fun processNewName(attrName: String, rule: RenamingRule): String? {
        return if (rule.doesMatchSourceName(attrName)) rule.getTargetName(attrName) else null
    }

    private fun testFolder(
        context: Context,
        uriString: String
    ): ImmutablePair<QueuingResult, Content>? {
        if (uriString.isEmpty()) {
            Timber.i("No download folder set") // May happen if user has skipped it during the intro
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_DOWNLOAD_FOLDER))
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }
        val rootFolder = FileHelper.getDocumentFromTreeUriString(context, uriString)
        if (null == rootFolder) {
            Timber.i("Download folder has not been found. Please select it again.") // May happen if the folder has been moved or deleted after it has been selected
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.DOWNLOAD_FOLDER_NOT_FOUND))
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }
        if (!FileHelper.isUriPermissionPersisted(context.contentResolver, rootFolder.uri)) {
            Timber.i("Insufficient credentials on download folder. Please select it again.")
            EventBus.getDefault()
                .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.DOWNLOAD_FOLDER_NO_CREDENTIALS))
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }
        val spaceLeftBytes = MemoryUsageFigures(context, rootFolder).getfreeUsageBytes()
        if (spaceLeftBytes < 2L * 1024 * 1024) {
            Timber.i("Device very low on storage space (<2 MB). Queue paused.")
            EventBus.getDefault().post(
                DownloadEvent.fromPauseMotive(
                    DownloadEvent.Motive.NO_STORAGE,
                    spaceLeftBytes
                )
            )
            return ImmutablePair(QueuingResult.QUEUE_END, null)
        }
        return null
    }
}