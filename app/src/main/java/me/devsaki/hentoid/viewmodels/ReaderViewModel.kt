package me.devsaki.hentoid.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.adapters.ImagePagerAdapter.ImageType
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.KRunnable
import me.devsaki.hentoid.core.READER_CACHE
import me.devsaki.hentoid.core.SEED_PAGES
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.RandomSeed
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DELETE_ASK_AGAIN
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DELETE_ASK_BOOK
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.chapterStr
import me.devsaki.hentoid.util.clearFileNameMatchCache
import me.devsaki.hentoid.util.coerceIn
import me.devsaki.hentoid.util.createImageListFromFiles
import me.devsaki.hentoid.util.deleteChapters
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.file.StorageCache
import me.devsaki.hentoid.util.file.extractArchiveEntriesCached
import me.devsaki.hentoid.util.file.findFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.formatCacheKey
import me.devsaki.hentoid.util.getPictureFilesFromContent
import me.devsaki.hentoid.util.image.MIME_IMAGE_AVIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_GIF
import me.devsaki.hentoid.util.image.MIME_IMAGE_JXL
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP
import me.devsaki.hentoid.util.image.MIME_VIDEO_MP4
import me.devsaki.hentoid.util.image.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.image.isImageAnimated
import me.devsaki.hentoid.util.matchFilesToImageList
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.persistJson
import me.devsaki.hentoid.util.purgeContent
import me.devsaki.hentoid.util.removePages
import me.devsaki.hentoid.util.renumberChapters
import me.devsaki.hentoid.util.reparseFromScratch
import me.devsaki.hentoid.util.scanArchivePdf
import me.devsaki.hentoid.util.scanBookFolder
import me.devsaki.hentoid.util.setAndSaveContentCover
import me.devsaki.hentoid.util.updateContentReadStats
import me.devsaki.hentoid.widget.ContentSearchManager
import me.devsaki.hentoid.widget.FolderSearchManager
import me.devsaki.hentoid.workers.BaseDeleteWorker
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.ReorderWorker
import me.devsaki.hentoid.workers.SplitMergeType
import me.devsaki.hentoid.workers.data.DeleteData
import me.devsaki.hentoid.workers.data.SplitMergeData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.Queue
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.roundToInt

private const val PRELOAD_RANGE = 6
private const val DOWNLOAD_RANGE = 6 // Sequential download; not concurrent
private const val EXTRACT_RANGE = 15

class ReaderViewModel(
    application: Application, private val dao: CollectionDAO
) : AndroidViewModel(application) {
    // Collection DAO
    private val contentSearchManager = ContentSearchManager()
    private val folderSearchManager = FolderSearchManager()

    // Collection data
    private val content = MutableLiveData<Content?>() // Current content

    // Content Ids of the whole collection ordered according to current filter
    private var contentIds = mutableListOf<Long>()

    // Files of the whole folder ordered according to current filter
    private var rootUri: Uri = Uri.EMPTY
    private var folderFiles = mutableListOf<Uri>()
    private var folderContentsCache = HashMap<Uri, Long>()

    private var currentContentIndex = -1 // Index of current content within the above list

    private var loadedContentId: Long = -1 // ID of currently loaded book


    // Pictures data
    private var currentImageSource: LiveData<List<ImageFile>>? = null

    // Set of image of current content
    private val databaseImages = MediatorLiveData<List<ImageFile>>()

    private val viewerImagesInternal = Collections.synchronizedList(ArrayList<ImageFile>())

    // Currently displayed set of images (reprocessed from databaseImages)
    private val viewerImages = MutableLiveData<List<ImageFile>>()

    private val startingIndex = MutableLiveData<Int>() // 0-based index of the current image

    private val shuffled = MutableLiveData<Boolean>() // Shuffle state of the current book

    private val reversed = MutableLiveData<Boolean>() // Reverse state of the current book

    // True during one loading where images need to be reloaded on screen
    private var forceImageUIReload = false

    // True if viewer only shows favourite images; false if shows all pages
    private val showFavouritesOnly = MutableLiveData<Boolean>()

    // Index of the thumbnail among loaded pages
    private var thumbIndex = 0

    // Write cache for read indicator (no need to update DB and JSON at every page turn)
    private val readPageNumbers: MutableSet<Int> = HashSet()

    // Kill switch to interrupt extracting when leaving the activity
    private val archiveExtractKillSwitch = AtomicBoolean(false)

    // Page indexes that are being downloaded
    private val indexDlInProgress = Collections.synchronizedSet(HashSet<Int>())

    // Page indexes that are being extracted
    private val indexExtractInProgress = Collections.synchronizedSet(HashSet<Int>())

    // FIFO kill switches to interrupt downloads when browsing the book
    private val downloadKillSwitches: Queue<AtomicBoolean> = ConcurrentLinkedQueue()

    // FIFO kill switches to interrupt preloads when browsing the book
    private val preloadKillSwitches: Queue<AtomicBoolean> = ConcurrentLinkedQueue()


    init {
        showFavouritesOnly.postValue(false)
        shuffled.postValue(false)
        reversed.postValue(false)
        StorageCache.addCleanupObserver(READER_CACHE, this.javaClass.name) { this.onCacheCleanup() }
    }

    override fun onCleared() {
        dao.cleanup()
        StorageCache.removeCleanupObserver(READER_CACHE, this.javaClass.name)
        super.onCleared()
    }

    fun getContent(): LiveData<Content?> {
        return content
    }

    fun getViewerImages(): LiveData<List<ImageFile>> {
        return viewerImages
    }

    fun getStartingIndex(): LiveData<Int> {
        return startingIndex
    }

    fun getShuffled(): LiveData<Boolean> {
        return shuffled
    }

    fun getShowFavouritesOnly(): LiveData<Boolean> {
        return showFavouritesOnly
    }

    // Artificial observer bound to the activity's lifecycle to ensure DB images are pushed to the ViewModel
    fun observeDbImages(activity: AppCompatActivity) {
        databaseImages.observe(activity) { }
    }

    /**
     * Load the given Content at the given page number
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     */
    fun loadContentFromId(contentId: Long, pageNumber: Int) {
        if (contentId > 0) {
            viewModelScope.launch {
                val loadedContent =
                    withContext(Dispatchers.IO) {
                        try {
                            dao.selectContent(contentId)
                        } finally {
                            dao.cleanup()
                        }
                    }
                if (loadedContent != null) {
                    if (contentIds.isEmpty()) contentIds.add(contentId)
                    loadContent(loadedContent, pageNumber)
                }
            }
            AchievementsManager.trigger(29)
        }
    }

    /**
     * Load the given Content at the given page number + preload all content IDs corresponding to the given search params
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     * @param bundle     ContentSearchBundle with the current filters and search criteria
     */
    fun loadContentFromContentSearch(contentId: Long, pageNumber: Int, bundle: Bundle) {
        contentSearchManager.loadFromBundle(bundle)
        loadContentFromContentSearch(contentId, pageNumber)
    }

    /**
     * Load the given Content at the given page number using the current state of SearchManager
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     */
    private fun loadContentFromContentSearch(contentId: Long, pageNumber: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val list = try {
                        contentSearchManager.searchContentIds(dao)
                    } finally {
                        dao.cleanup()
                    }
                    contentIds.clear()
                    contentIds.addAll(list)
                }
                loadContentFromId(contentId, pageNumber)
            } catch (e: Throwable) {
                Timber.w(e)
            }
        }
    }

    /**
     * Load the given resource + preload all Uri's corresponding to the given search params
     *
     * @param docUri     Uri of the resource (folder / archive / PDF) to load
     * @param bundle     FolderSearchBundle with the current filters and search criteria
     */
    fun loadContentFromFolderSearch(docUri: String, bundle: Bundle) {
        folderSearchManager.loadFromBundle(bundle)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rootUri = folderSearchManager.getRoot().toUri()
                val list = folderSearchManager.getFoldersFast(
                    getApplication(),
                    rootUri
                )
                folderFiles.clear()
                folderFiles.addAll(list.map { it.uri })
                folderContentsCache.clear()
                list.forEach { if (it.contentId > 0) folderContentsCache[it.uri] = it.contentId }
            }
            loadContentFromFile(docUri.toUri(), rootUri)
        }
    }

    private suspend fun loadContentFromFile(uri: Uri, rootUri: Uri) = withContext(Dispatchers.IO) {
        // Content has already been loaded from storage once
        folderContentsCache[uri]?.let {
            try {
                dao.selectContent(it)?.let { c ->
                    loadContent(c)
                    return@withContext
                }
            } finally {
                dao.cleanup()
            }
        }

        // Load from storage
        val ctx: Context = getApplication()
        val doc = getDocumentFromTreeUri(ctx, uri) ?: return@withContext
        val parent = getDocumentFromTreeUri(ctx, rootUri) ?: return@withContext
        val docName = doc.name ?: ""

        if (isSupportedArchive(docName) || "pdf" == getExtension(docName)) {
            val res = scanArchivePdf(
                ctx,
                parent.uri,
                doc,
                emptyList(),
                StatusContent.STORAGE_RESOURCE,
                null
            )
            if (0 == res.first) res.second?.let {
                it.id = addContent(ctx, dao, it)
                loadContent(it)
            }
        } else {
            val res = scanBookFolder(
                ctx,
                parent.uri,
                doc,
                emptyList(),
                StatusContent.STORAGE_RESOURCE
            )
            res.id = addContent(ctx, dao, res)
            folderContentsCache[uri] = res.id
            loadContent(res)
        }
    }


    fun loadFavPages() {
        // Forge content with the images alone
        val c = Content()
        c.id = Long.MAX_VALUE
        c.site = Site.NONE
        contentIds.clear()
        contentIds.add(c.id)
        currentContentIndex = 0
        c.isFirst = true
        c.isLast = true
        c.folderExists = false
        c.isDynamic = true
        content.postValue(c)

        if (currentImageSource != null) databaseImages.removeSource(currentImageSource!!)
        currentImageSource = dao.selectAllFavouritePagesLive()
        databaseImages.addSource(currentImageSource!!) { imgs ->
            loadImages(c, -1, imgs.toMutableList())
        }
    }

    /**
     * Set the given index as the picture viewer's starting index
     *
     * @param index Page index to set
     */
    fun setViewerStartingIndex(index: Int) {
        startingIndex.postValue(index)
    }

    /**
     * Set the given page's index as the picture viewer's starting index
     *
     * @param imageId ID of the page to set
     */
    fun setViewerStartingIndexById(imageId: Long) {
        val index = viewerImagesInternal.indexOfFirst { it.id == imageId }
        if (index > -1) startingIndex.postValue(index)
    }

    /**
     * Process the given raw ImageFile entries to populate the viewer
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with at first load
     * @param newImages  Images to process
     */
    private fun loadImages(
        theContent: Content,
        pageNumber: Int,
        newImages: MutableList<ImageFile>
    ) {
        databaseImages.postValue(newImages)
        if (forceImageUIReload) {
            newImages.forEach { it.isForceRefresh = true }
            forceImageUIReload = false
        }

        // Don't reload from storage / archive again if the image list hasn't changed
        // e.g. page favourited
        if (!theContent.isArchive && !theContent.isPdf) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    processStorageImages(theContent, newImages)
                    cacheJson(getApplication<Application>().applicationContext, theContent)
                }
                processImages(theContent, -1, newImages)
            }
        } else { // Archive or PDF
            // Copy location properties of the new list on the current list
            for (i in newImages.indices) {
                val newImg = newImages[i]
                StorageCache.getFile(READER_CACHE, formatCacheKey(newImg))?.let {
                    newImg.displayUri = it.toString()
                }
            }
            processImages(theContent, pageNumber, newImages)
        }
    }

    /**
     * Process the given raw ImageFile entries to populate the viewer, loading the images directly from the device's storage
     *
     * @param theContent Content to use
     * @param newImages  Images to process
     */
    private suspend fun processStorageImages(
        theContent: Content,
        newImages: MutableList<ImageFile>
    ) = withContext(Dispatchers.IO) {
        require(!theContent.isArchive && !theContent.isPdf) { "Content must not be an archive" }

        // Make sure all image files are attached
        val missingUris = newImages.any { it.fileUri.isEmpty() }
        val newImageFiles =
            if (missingUris || newImages.isEmpty()) reattachImageFiles(theContent, newImages)
            else ArrayList(newImages)

        // Replace initial images with updated images
        newImages.clear()
        newImages.addAll(newImageFiles)
    }

    private suspend fun reattachImageFiles(
        c: Content,
        newImages: List<ImageFile>
    ): List<ImageFile> {
        var newImageFiles: List<ImageFile> = ArrayList(newImages)
        val pictureFiles = getPictureFilesFromContent(getApplication(), c)
        if (pictureFiles.isNotEmpty()) {
            if (newImages.isEmpty()) {
                newImageFiles = createImageListFromFiles(pictureFiles)
                c.setImageFiles(newImageFiles)
                try {
                    dao.insertContent(c)
                } finally {
                    dao.cleanup()
                }
            } else {
                // Match files for viewer display; no need to persist that
                matchFilesToImageList(pictureFiles, newImageFiles)
            }
        } else { // Try to get some from the cache
            newImageFiles.forEach {
                StorageCache.getFile(READER_CACHE, formatCacheKey(it))?.let { existingUri ->
                    it.displayUri = existingUri.toString()
                }
            }
        }
        return newImageFiles
    }

    /**
     * Callback to run when the activity is on the verge of being destroyed
     */
    fun onActivityLeave() {
        viewModelScope.launch(Dispatchers.IO) {
            AchievementsManager.checkCollection()
            clearFileNameMatchCache()
            dao.cleanup()
        }
    }

    /**
     * Initialize the picture viewer using the given parameters
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with at first load
     * @param imageFiles Pictures to process
     */
    private fun processImages(
        theContent: Content,
        pageNumber: Int,
        imageFiles: List<ImageFile>
    ) {
        val bookChanged = theContent.id != loadedContentId
        val startingIndex =
            if (bookChanged) computeStartingIndex(theContent, pageNumber, imageFiles) else -1
        sortAndSetViewerImages(
            imageFiles,
            getShuffled().value == true,
            reversed.value == true,
            !bookChanged,
            startingIndex
        )
        if (bookChanged) contentFirstLoad(startingIndex, imageFiles)
        loadedContentId = theContent.id
    }

    private fun adjustPageIndex(index: Int, imageFiles: List<ImageFile>): Int {
        var result = index

        // Correct offset with the thumb index
        thumbIndex = -1
        for (i in imageFiles.indices) if (!imageFiles[i].isReadable) {
            thumbIndex = i
            break
        }
        // Ignore if it doesn't intervene
        if (thumbIndex == result) result += 1
        else if (thumbIndex > result) thumbIndex = 0

        return 0.coerceAtLeast(result - thumbIndex - 1)
    }

    private fun computeStartingIndex(
        theContent: Content,
        pageNumber: Int,
        imageFiles: List<ImageFile>
    ): Int {
        var startingIndex = 0

        // Auto-restart at last read position if asked to
        if (Settings.isReaderResumeLastLeft && theContent.lastReadPageIndex > -1)
            startingIndex = theContent.lastReadPageIndex

        // Start at the given page number, if any
        if (pageNumber > -1) {
            imageFiles.forEachIndexed { index, it ->
                if (it.order == pageNumber) {
                    startingIndex = index + 1
                    return@forEachIndexed
                }
            }
        }

        return adjustPageIndex(startingIndex, imageFiles)
    }

    /**
     * Initialize the picture viewer using the given parameters
     * (used only once per book when it is loaded for the first time)
     *
     * @param startingIndex Index of the first page to load
     * @param imageFiles    Pictures to process
     */
    private fun contentFirstLoad(
        startingIndex: Int,
        imageFiles: List<ImageFile>
    ) {
        // Init the read pages write cache
        readPageNumbers.clear()

        // Mark initial page as read
        if (startingIndex < imageFiles.size) markPageAsRead(imageFiles[startingIndex].order)
    }

    /**
     * Toggle the shuffle mode
     */
    fun toggleShuffle() {
        val isShuffled = getShuffled().value != true
        if (isShuffled) RandomSeed.renewSeed(SEED_PAGES)
        shuffled.postValue(isShuffled)
        databaseImages.value?.let {
            sortAndSetViewerImages(it, isShuffled, reversed.value == true, true)
        }
    }

    /**
     * Reverse page order
     */
    fun reverse() {
        val isReversed = reversed.value != true
        reversed.postValue(isReversed)
        databaseImages.value?.let {
            sortAndSetViewerImages(it, shuffled.value == true, isReversed, true)
        }
    }

    /**
     * Sort and set the given images for the viewer
     *
     * @param images     Images to process
     * @param shuffle    True if shuffle mode is on; false if not
     * @param reverse    True if reverse mode is on; false if not
     * @param startIndex Page index to start with (-1 when not applicable)
     */
    private fun sortAndSetViewerImages(
        images: List<ImageFile>,
        shuffle: Boolean,
        reverse: Boolean,
        canReuse: Boolean,
        startIndex: Int = -1
    ) {
        var imgs = images.toList()
        imgs = if (shuffle) {
            imgs.shuffled(Random(RandomSeed.getSeed(SEED_PAGES)))
        } else {
            // Sort images according to their Order; don't keep the cover thumb
            imgs.sortedBy { it.order * if (reverse) -1 else 1 }
        }
        // Don't keep the cover thumb
        imgs = imgs.filter { it.isReadable }
        if (true == getShowFavouritesOnly().value) {
            imgs = imgs.filter { it.favourite }
        }

        // Populate / restore transient attributes
        for (i in imgs.indices) {
            imgs[i].displayOrder = i
            if (canReuse) {
                val id = imgs[i].id
                viewerImagesInternal.first { it.id == id }.let {
                    imgs[i].displayUri = it.displayUri
                }
            }
        }

        // Only update if there's any noticeable difference on images...
        var hasDiff = imgs.size != viewerImagesInternal.size
        if (!hasDiff) {
            for (i in imgs.indices) {
                hasDiff = imgs[i] != viewerImagesInternal[i]
                if (hasDiff) break
            }
        }
        // ...or chapters
        if (!hasDiff) {
            val oldChapters = viewerImagesInternal.map { it.linkedChapter }
            val newChapters = imgs.map { it.linkedChapter }.toList()
            hasDiff = oldChapters.size != newChapters.size
            if (!hasDiff) {
                for (i in oldChapters.indices) {
                    hasDiff = oldChapters[i] != newChapters[i]
                    if (hasDiff) break
                }
            }
        }
        if (hasDiff) {
            synchronized(viewerImagesInternal) {
                viewerImagesInternal.clear()
                viewerImagesInternal.addAll(imgs)
            }
            if (startIndex > -1) onPageChange(startIndex - 1, 1, true)
            else viewerImages.postValue(viewerImagesInternal.toList())
        }
    }

    /**
     * Callback to run whenever a book is left (e.g. using previous/next or leaving activity)
     *
     * @param viewerIndex Viewer index of the active page when the user left the book
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun onLeaveBook(viewerIndex: Int) {
        if (VIEWER_DELETE_ASK_BOOK == Settings.readerDeleteAskMode)
            Settings.readerDeleteAskMode = VIEWER_DELETE_ASK_AGAIN
        indexDlInProgress.clear()
        indexExtractInProgress.clear()
        archiveExtractKillSwitch.set(true)

        // Don't do anything if the Content hasn't even been loaded
        if (-1L == loadedContentId) return
        val theImages = databaseImages.value

        // We do need GlobalScope to carry these beyond current lifecycleScope
        GlobalScope.launch {
            val theContent = try {
                dao.selectContent(loadedContentId)
            } finally {
                dao.cleanup()
            }
            if (null == theImages || null == theContent) return@launch
            val nbReadablePages = theImages.count { it.isReadable }
            val readThresholdPosition: Int = when (Settings.readerPageReadThreshold) {
                Settings.Value.VIEWER_READ_THRESHOLD_1 -> 1
                Settings.Value.VIEWER_READ_THRESHOLD_2 -> 2
                Settings.Value.VIEWER_READ_THRESHOLD_5 -> 5
                Settings.Value.VIEWER_READ_THRESHOLD_ALL -> nbReadablePages
                else -> nbReadablePages
            }
            val completedThresholdRatio: Float =
                when (Settings.readerRatioCompletedThreshold) {
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_10 -> 0.1f
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_25 -> 0.25f
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_33 -> 0.33f
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_50 -> 0.5f
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_75 -> 0.75f
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_ALL -> 1f
                    Settings.Value.VIEWER_COMPLETED_RATIO_THRESHOLD_NONE -> 2f
                    else -> 2f
                }
            val completedThresholdPosition =
                (completedThresholdRatio * nbReadablePages).roundToInt()
            val collectionIndex =
                viewerIndex + if (-1 == thumbIndex || thumbIndex > viewerIndex) 0 else 1
            val isLastPage = viewerIndex >= viewerImagesInternal.size - 1
            val indexToSet = if (isLastPage) 0 else collectionIndex
            val updateReads =
                readPageNumbers.size >= readThresholdPosition || theContent.reads > 0
            val markAsComplete = readPageNumbers.size >= completedThresholdPosition

            try {
                doLeaveBook(theContent.id, indexToSet, updateReads, markAsComplete)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Perform the I/O operations to persist book information upon leaving
     *
     * @param contentId       ID of the Content to save
     * @param indexToSet      DB page index to set as the last read page
     * @param updateReads     True if number of reads have to be updated; false if not
     * @param markAsCompleted True if the book has to be marked as completed
     */
    private suspend fun doLeaveBook(
        contentId: Long, indexToSet: Int, updateReads: Boolean, markAsCompleted: Boolean
    ) = withContext(Dispatchers.IO) {
        // Use a brand new DAO for that (the viewmodel's DAO may be in the process of being cleaned up)
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            // Get a fresh version of current content in case it has been updated since the initial load
            // (that can be the case when viewing a book that is being downloaded)
            val savedContent = dao.selectContent(contentId) ?: return@withContext
            val theImages = savedContent.imageFiles

            // Update image read status with the cached read statuses
            val previousReadPageNumbers =
                theImages.filter { it.read && it.isReadable }.map { it.order }.toSet()
            val reReadPagesNumbers = readPageNumbers.toMutableSet()
            reReadPagesNumbers.retainAll(previousReadPageNumbers)
            if (readPageNumbers.size > reReadPagesNumbers.size) {
                for (img in theImages) if (readPageNumbers.contains(img.order)) img.read = true
                savedContent.computeReadProgress()
            }
            if (indexToSet != savedContent.lastReadPageIndex || updateReads || readPageNumbers.size > reReadPagesNumbers.size || savedContent.completed != markAsCompleted)
                updateContentReadStats(
                    getApplication(),
                    dao,
                    savedContent,
                    theImages,
                    indexToSet,
                    updateReads,
                    markAsCompleted
                )
        } finally {
            dao.cleanup()
        }
    }

    /**
     * Set the filter for favourite pages to the target state
     *
     * @param targetState Target state of the favourite pages filter
     */
    fun filterFavouriteImages(targetState: Boolean) {
        if (loadedContentId > -1) {
            showFavouritesOnly.postValue(targetState)
            contentSearchManager.setFilterPageFavourites(targetState)
            loadContentFromContentSearch(loadedContentId, -1)
        }
    }

    /**
     * Toggle the favourite status of the page at the given viewer index
     *
     * @param viewerIndex     Viewer index of the page whose status to toggle
     * @param successCallback Callback to be called on success
     */
    fun toggleImageFavourite(viewerIndex: Int, successCallback: (Boolean) -> Unit) {
        val file = viewerImagesInternal[viewerIndex]
        val newState = !file.favourite
        toggleImageFavourite(listOf(file)) {
            successCallback.invoke(newState)
        }
    }

    /**
     * Toggle the favourite status of the given pages
     *
     * @param images          Pages whose status to toggle
     * @param successCallback Callback to be called on success
     */
    fun toggleImageFavourite(images: List<ImageFile>, successCallback: Runnable) {
        viewModelScope.launch {
            try {
                doToggleImageFavourite(images)
                successCallback.run()
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Toggles page favourite flag in DB and in the content JSON
     *
     * @param images images whose flag to toggle
     */
    private suspend fun doToggleImageFavourite(images: List<ImageFile>) =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext

            try {
                val theContent = dao.selectContent(images[0].content.targetId) ?: return@withContext

                // We can't work on the given objects as they are tied to the UI (part of ImageFileItem)
                val dbImages = theContent.imageFiles
                for (img in images)
                    for (dbImg in dbImages)
                        if (img.id == dbImg.id) {
                            dbImg.favourite = !dbImg.favourite
                            break
                        }

                // Persist in DB
                dao.insertImageFiles(dbImages)

                // Persist new values in JSON
                theContent.setImageFiles(dbImages)
                persistJson(getApplication<Application>().applicationContext, theContent)
            } finally {
                dao.cleanup()
            }
        }

    /**
     * Toggle the favourite flag of the given Content
     *
     * @param successCallback Callback to be called on success
     */
    fun toggleContentFavourite(viewerIndex: Int, successCallback: (Boolean) -> Unit) {
        val theContent = getContent().value ?: return
        val newState = !theContent.favourite

        viewModelScope.launch {
            try {
                doToggleContentFavourite(theContent)
                reloadContent(viewerIndex) // Must run on the main thread
                successCallback.invoke(newState)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Toggle the favourite flag of the given Content in DB and in the content JSON
     *
     * @param content content whose flag to toggle
     */
    private suspend fun doToggleContentFavourite(content: Content) = withContext(Dispatchers.IO) {
        content.favourite = !content.favourite

        // Persist in DB
        try {
            dao.insertContent(content)
        } finally {
            dao.cleanup()
        }

        // Persist new values in JSON
        persistJson(getApplication<Application>().applicationContext, content)
    }

    /**
     * Set the given rating for the current content
     */
    fun setContentRating(rating: Int, successCallback: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val targetContent = try {
                        val targetContent = dao.selectContent(loadedContentId) ?: return@withContext
                        targetContent.rating = rating
                        dao.insertContent(targetContent)
                        targetContent
                    } finally {
                        dao.cleanup()
                    }
                    persistJson(
                        getApplication<Application>().applicationContext, targetContent
                    )
                }
                successCallback.invoke(rating)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Delete the current Content
     *
     * @param onError Callback to use in case an error occurs
     */
    fun deleteContent(onError: (Throwable) -> Unit) {

        var targetContent: Content? = null
        try {
            targetContent = dao.selectContent(loadedContentId)
                ?: throw IllegalArgumentException("Content $loadedContentId not found")

            // Unplug image source listener (avoid displaying pages as they are being deleted; it messes up with DB transactions)
            currentImageSource?.let { databaseImages.removeSource(it) }

            val builder = DeleteData.Builder()
            builder.setOperation(BaseDeleteWorker.Operation.DELETE)
            builder.setQueueIds(listOf(loadedContentId))

            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueueUniqueWork(
                R.id.delete_service_delete.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(DeleteWorker::class.java).setInputData(builder.data)
                    .build()
            )
            onContentRemoved()
        } catch (t: Throwable) {
            onError.invoke(t)
            // Restore image source listener on error
            currentImageSource?.let { src ->
                targetContent?.let {
                    databaseImages.addSource(src) { imgs ->
                        loadImages(it, -1, imgs.toMutableList())
                    }
                }
            }
        } finally {
            dao.cleanup()
        }
    }

    private fun onContentRemoved() {
        currentImageSource = null
        // Switch to the next book if the list is populated (multi-book)
        if (contentIds.isNotEmpty()) {
            contentIds.removeAt(currentContentIndex)
            if (currentContentIndex >= contentIds.size && currentContentIndex > 0) currentContentIndex--
            if (contentIds.size > currentContentIndex) loadContentFromId(
                contentIds[currentContentIndex], -1
            )
        } else { // Close the viewer if the list is empty (single book)
            content.postValue(null)
        }
    }

    /**
     * Delete the page at the given viewer index
     *
     * @param pageViewerIndex Viewer index of the page to delete
     * @param onError         Callback to run in case of error
     */
    fun deletePage(pageViewerIndex: Int, onError: (Throwable) -> Unit) {
        val imageFiles = viewerImagesInternal
        if (imageFiles.size > pageViewerIndex && pageViewerIndex > -1)
            deletePages(listOf(imageFiles[pageViewerIndex]), onError)
    }

    /**
     * Delete the given pages
     *
     * @param pages   Pages to delete
     * @param onError Callback to run in case of error
     */
    fun deletePages(pages: List<ImageFile>, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                removePages(pages, dao, getApplication())
            } catch (t: Throwable) {
                Timber.e(t)
                onError.invoke(t)
            }
        }
    }

    /**
     * Set the given image as the current Content's cover
     *
     * @param page Page to set as the current Content's cover
     */
    fun setCover(page: ImageFile) {
        viewModelScope.launch {
            try {
                setAndSaveContentCover(getApplication(), page, dao)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Load the next Content according to the current filter & search criteria
     *
     * @param viewerIndex Page viewer index the current Content has been left on
     * @return true if there's a book to load; false if there is none
     */
    fun loadNextContent(viewerIndex: Int): Boolean {
        val max = if (contentIds.isNotEmpty()) contentIds.size else folderFiles.size
        if (currentContentIndex < max - 1) {
            currentContentIndex++
            onLeaveBook(viewerIndex)
            reloadContent()
            return true
        }
        return false
    }

    /**
     * Load the previous Content according to the current filter & search criteria
     *
     * @param viewerIndex Page viewer index the current Content has been left on
     * @return true if there's a book to load; false if there is none
     */
    fun loadPreviousContent(viewerIndex: Int): Boolean {
        if (currentContentIndex > 0) {
            currentContentIndex--
            onLeaveBook(viewerIndex)
            reloadContent()
            return true
        }
        return false
    }

    /**
     * Load the first Content according to the current filter & search criteria
     *
     * @param viewerIndex Page viewer index the current Content has been left on
     */
    fun loadFirstContent(viewerIndex: Int) {
        currentContentIndex = 0
        onLeaveBook(viewerIndex)
        reloadContent(1)
    }

    private fun reloadContent(viewerIndex: Int = -1) {
        viewModelScope.launch {
            if (contentIds.isNotEmpty()) {
                loadContentFromId(contentIds[currentContentIndex], -1)
            } else if (folderFiles.isNotEmpty()) {
                loadContentFromFile(folderFiles[currentContentIndex], rootUri)
            }
        }
        if (viewerIndex > -1) setViewerStartingIndex(viewerIndex)
    }

    /**
     * Load the given content at the given page number
     *
     * @param c Content to load
     * @param pageNumber Page number to start with
     */
    private suspend fun loadContent(c: Content, pageNumber: Int = -1) {
        Settings.readerCurrentContent = c.id
        var listSize: Int
        currentContentIndex = if (c.status == StatusContent.STORAGE_RESOURCE) {
            listSize = folderFiles.size
            folderFiles.indexOf(c.storageUri.toUri())
        } else {
            listSize = contentIds.size
            contentIds.indexOf(c.id)
        }
        if (-1 == currentContentIndex) currentContentIndex = 0
        c.isFirst = 0 == currentContentIndex
        c.isLast = currentContentIndex >= listSize - 1
        withContext(Dispatchers.IO) {
            if (null == getDocumentFromTreeUriString(getApplication(), c.storageUri))
                c.folderExists = false
        }
        content.postValue(c)
        loadDatabaseImages(c, pageNumber)
    }

    /**
     * Load the given Content's pictures from the database and process them, initializing the viewer to start at the given page number
     *
     * @param theContent Content to load the pictures for
     * @param pageNumber Page number to start with
     */
    private suspend fun loadDatabaseImages(
        theContent: Content,
        pageNumber: Int = -1
    ) = withContext(Dispatchers.Main) {
        // Observe the content's images
        // NB : It has to be dynamic to be updated when viewing a book from the queue screen
        synchronized(databaseImages) {
            currentImageSource?.let { databaseImages.removeSource(it) }
            currentImageSource = try {
                dao.selectImagesFromContentLive(theContent.id, true)
            } finally {
                dao.cleanup()
            }
            currentImageSource?.let {
                databaseImages.addSource(it) { s ->
                    loadImages(theContent, pageNumber, s.toMutableList())
                }
            }
        }
    }

    /**
     * Update local preferences for the current Content
     *
     * @param newPrefs Preferences to replace the current Content's local preferences
     */
    fun updateContentPreferences(newPrefs: Map<String, String>, viewerIndex: Int) {
        viewModelScope.launch {
            try {
                var theContent: Content?
                withContext(Dispatchers.IO) {
                    try {
                        theContent = dao.selectContent(loadedContentId)
                        theContent?.let {
                            it.bookPreferences = newPrefs
                            dao.insertContent(it)
                        }
                    } finally {
                        dao.cleanup()
                    }
                }
                forceImageUIReload = true
                reloadContent(viewerIndex) // Must run on the main thread
                withContext(Dispatchers.IO) {
                    // Persist in JSON
                    theContent?.let {
                        persistJson(getApplication(), it)
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Cache the JSON URI of the given Content in the database to speed up favouriting
     *
     * @param context Context to use
     * @param content Content to cache the JSON URI for
     */
    private suspend fun cacheJson(context: Context, content: Content) =
        withContext(Dispatchers.IO) {
            if (content.jsonUri.isNotEmpty() || content.isArchive) return@withContext
            val folder =
                getDocumentFromTreeUriString(context, content.storageUri) ?: return@withContext
            val foundFile = findFile(getApplication(), folder, JSON_FILE_NAME_V2)
            if (null == foundFile) {
                Timber.e("JSON file not detected in %s", content.storageUri)
                return@withContext
            }

            // Cache the URI of the JSON to the database
            content.jsonUri = foundFile.uri.toString()
            try {
                dao.insertContent(content)
            } finally {
                dao.cleanup()
            }
        }

    /**
     * Mark the given page number as read
     *
     * @param pageNumber Page number to mark as read
     */
    fun markPageAsRead(pageNumber: Int) {
        readPageNumbers.add(pageNumber)
    }

    fun clearForceReload() {
        synchronized(viewerImagesInternal) {
            viewerImagesInternal.forEach { it.isForceRefresh = false }
        }
    }

    /**
     * Force all images to be reposted
     */
    fun repostImages() {
        viewerImages.postValue(viewerImages.value)
    }

    /**
     * Handler to call when changing page
     *
     * @param viewerIndex Viewer index of the page that has just been displayed
     * @param direction   Direction the viewer is going to (1 : forward; -1 : backward; 0 : no movement)
     */
    fun onPageChange(viewerIndex: Int, direction: Int, setIndex: Boolean = false) {
        var indexSet = false
        onPageChange(viewerIndex, direction) {
            // Instanciate a new list to trigger an actual Adapter UI refresh
            viewerImages.postValue(ArrayList(viewerImagesInternal))
            if (setIndex && !indexSet) {
                setViewerStartingIndex(viewerIndex + 1)
                indexSet = true
            }
        }
    }

    private fun onPageChange(viewerIndex: Int, direction: Int, onDoneAfterPreload: KRunnable?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (viewerImagesInternal.size <= viewerIndex) return@launch
            val theContent = getContent().value ?: return@launch
            val isArchive = theContent.isArchive
            val isPdf = theContent.isPdf
            val isStreamed = DownloadMode.STREAM == theContent.downloadMode
            val isStored = !isArchive && !isPdf && !isStreamed && !theContent.isDynamic
            val picturesLeftToProcess = IntRange(0, viewerImagesInternal.size - 1)
                .filter { isPictureNeedsProcessing(it, viewerImagesInternal) }.toSet()
            if (picturesLeftToProcess.isEmpty()) return@launch

            // Identify pages to be loaded
            val setToLoad: MutableSet<Int> = HashSet()
            val increment = if (direction >= 0) 1 else -1
            val quantity = if (isArchive || isPdf) EXTRACT_RANGE
            else if (isStreamed) DOWNLOAD_RANGE
            else PRELOAD_RANGE
            // pageIndex at 1/3rd of the range to extract/download
            // (2/3 forward; 1/3 backwards just in case)
            // => determine its bound
            val initialIndex = coerceIn(
                1f * viewerIndex - quantity * increment / 3f,
                0f,
                (viewerImagesInternal.size - 1).toFloat()
            ).toInt()
            for (i in 0 until quantity)
                if (picturesLeftToProcess.contains(initialIndex + increment * i))
                    setToLoad.add(initialIndex + increment * i)

            // Load by distance from current index with a preference for forward browsing (e.g. 2-3-1-4-0)
            val indexesToLoad: MutableList<Int> = ArrayList()
            for (distance in 0..quantity) {
                if (setToLoad.contains(viewerIndex + distance)) indexesToLoad.add(viewerIndex + distance)
                if (distance != 0 && setToLoad.contains(viewerIndex - distance))
                    indexesToLoad.add(viewerIndex - distance)
            }

            if (BuildConfig.DEBUG) {
                val sb = StringBuilder()
                indexesToLoad.forEach { sb.append(it).append("-") }
                Timber.v("IndexesToLoad $sb")
            }

            // Preload image types
            preloadImageTypes(indexesToLoad.toList(), onDoneAfterPreload)

            if (isStored) return@launch

            // Only run extraction when there's at least 1/3rd of the extract range to fetch
            // (prevents calling extraction for one single picture at every page turn)
            var greenlight = true
            if (isArchive || isPdf) {
                greenlight = indexesToLoad.size >= EXTRACT_RANGE / 3f
                if (!greenlight) {
                    val from = if (increment > 0) initialIndex else 0
                    val to = if (increment > 0) viewerImagesInternal.size else initialIndex + 1
                    val leftToProcessDirection =
                        IntRange(from, to - 1).count { picturesLeftToProcess.contains(it) }
                    greenlight = indexesToLoad.size == leftToProcessDirection
                }
            }
            if (indexesToLoad.isEmpty() || !greenlight) return@launch

            // Populate what's already cached
            val cachedIndexes = HashSet<Int>()
            var nbProcessed = 0
            synchronized(viewerImagesInternal) {
                indexesToLoad.forEach { idx ->
                    nbProcessed++
                    viewerImagesInternal[idx].let { img ->
                        val key = formatCacheKey(img)
                        if (StorageCache.peekFile(READER_CACHE, key)) {
                            updateImgWithExtractedUri(
                                img,
                                idx,
                                StorageCache.getFile(READER_CACHE, key)!!,
                                0 == cachedIndexes.size % 4 || nbProcessed == indexesToLoad.size
                            )
                            cachedIndexes.add(idx)
                        }
                    }
                }
            }
            dao.cleanup()
            indexesToLoad.removeAll(cachedIndexes)

            // Unarchive
            // Group by archive for efficiency
            val indexesByArchive = indexesToLoad.filter { viewerImagesInternal[it].isArchived }
                .groupBy { viewerImagesInternal[it].content.target.storageUri }.toMap()
            indexesByArchive.keys.forEach {
                getFileFromSingleUriString(getApplication(), it)?.let { archiveFile ->
                    extractPics(indexesByArchive[it]!!, archiveFile, false)
                }
            }

            // Un-PDF
            val indexesByPdf = indexesToLoad.filter { viewerImagesInternal[it].isPdf }
                .groupBy { viewerImagesInternal[it].content.target.storageUri }.toMap()
            indexesByPdf.keys.forEach {
                getFileFromSingleUriString(getApplication(), it)?.let { pdfFile ->
                    extractPics(indexesByPdf[it]!!, pdfFile, true)
                }
            }

            // Download
            val onlineIndexes =
                indexesToLoad.filter { viewerImagesInternal[it].status == StatusContent.ONLINE }
            downloadPics(onlineIndexes)

            dao.cleanup()
        }
    }

    /**
     * Indicate if the picture at the given page index in the given list needs processing
     * (i.e. downloading or extracting)
     *
     * @param pageIndex  Index to test
     * @param images     List of pictures to test against
     * @return True if the picture at the given index needs processing; false if not
     */
    private fun isPictureNeedsProcessing(
        pageIndex: Int,
        images: List<ImageFile>
    ): Boolean {
        if (pageIndex < 0 || images.size <= pageIndex) return false
        images[pageIndex].let {
            return (it.isOnline || // Image has to be downloaded
                    it.isArchived || // Image has to be extracted from an archive
                    it.isPdf // Image has to be extracted from a PDF
                    )
                    || it.imageType == ImageType.IMG_TYPE_UNSET // Neither downloadable not extractable, but needs a preload
        }
    }

    /**
     * Download the pictures at the given indexes to the given folder
     * NB : loading happens in the background but is sequential
     *
     * @param indexesToLoad DB indexes of the pictures to download
     */
    private fun downloadPics(indexesToLoad: List<Int>) {
        for (index in indexesToLoad) {
            if (indexDlInProgress.contains(index)) continue
            indexDlInProgress.add(index)

            // Adjust the current queue
            while (downloadKillSwitches.size >= DOWNLOAD_RANGE) {
                val stopDownload = downloadKillSwitches.poll()
                stopDownload?.set(true)
                Timber.d("Aborting a download")
            }
            // Schedule a new download
            val stopDownload = AtomicBoolean(false)
            downloadKillSwitches.add(stopDownload)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val resultOpt = downloadPic(index, stopDownload)
                    indexDlInProgress.remove(index)
                    if (null == resultOpt) { // Nothing to download
                        Timber.d("NO IMAGE FOUND AT INDEX %d", index)
                        notifyDownloadProgress(-1f, index)
                        return@launch
                    }
                    val downloadedPageIndex = resultOpt.first
                    synchronized(viewerImagesInternal) {
                        if (viewerImagesInternal.size <= downloadedPageIndex) return@launch

                        // Instanciate a new ImageFile not to modify the one used by the UI
                        val downloadedPic = ImageFile(viewerImagesInternal[downloadedPageIndex])
                        downloadedPic.displayUri = resultOpt.second
                        viewerImagesInternal.removeAt(downloadedPageIndex)
                        viewerImagesInternal.add(downloadedPageIndex, downloadedPic)
                        Timber.d("REPLACING ${downloadedPic.id} [idx $downloadedPageIndex / order ${downloadedPic.order}] -> ${downloadedPic.displayUri}")
                        preloadImageTypes(listOf(downloadedPageIndex)) {
                            // Instanciate a new list to trigger an actual Adapter UI refresh
                            viewerImages.postValue(ArrayList(viewerImagesInternal))
                        }
                    }
                } catch (t: Throwable) {
                    Timber.w(t)
                } finally {
                    dao.cleanup()
                }
            }
        }
    }

    /**
     * Extract the picture files at the given indexes from the given archive to the given folder
     *
     * @param indexesToLoad DB indexes of the pictures to download
     * @param archiveFile   Archive file to extract from
     */
    private fun extractPics(
        indexesToLoad: List<Int>,
        archiveFile: DocumentFile,
        isPdf: Boolean
    ) {
        viewModelScope.launch {
            try {
                doExtractPics(indexesToLoad, archiveFile, isPdf)
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private suspend fun doExtractPics(
        indexesToLoad: List<Int>,
        archiveFile: DocumentFile,
        isPdf: Boolean
    ) = withContext(Dispatchers.IO) {
        // Interrupt current extracting process, if any
        if (indexExtractInProgress.isNotEmpty()) {
            archiveExtractKillSwitch.set(true)
            // Wait until extraction has actually stopped
            var remainingIterations = 15 // Timeout
            do {
                pause(500)
            } while (indexExtractInProgress.isNotEmpty() && remainingIterations-- > 0)
            if (indexExtractInProgress.isNotEmpty()) return@withContext
        }

        // Reset interrupt state to make sure extraction runs
        archiveExtractKillSwitch.set(false)

        // Build extraction instructions, ignoring already extracted items
        val extractInstructions: MutableList<Triple<String, Long, String>> = ArrayList()
        var hasExistingUris = false
        for (index in indexesToLoad) {
            if (index < 0 || index >= viewerImagesInternal.size) continue
            val img = viewerImagesInternal[index]
            val c = img.linkedContent ?: continue

            val existingUri = StorageCache.getFile(READER_CACHE, formatCacheKey(img))
            if (existingUri != null) {
                updateImgWithExtractedUri(img, index, existingUri, false)
                hasExistingUris = true
            } else {
                extractInstructions.add(
                    Triple(
                        img.fileUri.replace(c.storageUri + File.separator, ""),
                        img.id,
                        formatCacheKey(img)
                    )
                )
                indexExtractInProgress.add(index)
            }
        }
        dao.cleanup()
        if (hasExistingUris) viewerImages.postValue(ArrayList(viewerImagesInternal))
        if (extractInstructions.isEmpty()) return@withContext

        Timber.d(
            "Extracting %d files starting from index %s",
            extractInstructions.size,
            indexesToLoad[0]
        )

        val nbProcessed = AtomicInteger()

        try {
            if (isPdf) {
                val pdfManager = PdfManager()
                pdfManager.extractImagesCached(
                    getApplication<Application>().applicationContext,
                    archiveFile,
                    extractInstructions,
                    { archiveExtractKillSwitch.get() },
                    { id, uri ->
                        onResourceExtracted(
                            id,
                            uri,
                            nbProcessed,
                            indexesToLoad.size
                        )
                    },
                    { onExtractionComplete(nbProcessed, indexesToLoad.size) })
            } else {
                getApplication<Application>().applicationContext.extractArchiveEntriesCached(
                    archiveFile.uri,
                    extractInstructions,
                    { archiveExtractKillSwitch.get() },
                    { id, uri ->
                        onResourceExtracted(
                            id,
                            uri,
                            nbProcessed,
                            indexesToLoad.size
                        )
                    },
                    { onExtractionComplete(nbProcessed, indexesToLoad.size) })
            }
        } catch (e: Exception) {
            Timber.d(e)
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.COMPLETE,
                    R.id.viewer_load,
                    0,
                    nbProcessed.get(),
                    0,
                    indexesToLoad.size
                )
            )
            indexExtractInProgress.clear()
            archiveExtractKillSwitch.set(false)
        }
        dao.cleanup()
    }

    private fun onResourceExtracted(
        identifier: Long,
        uri: Uri,
        nbProcessed: AtomicInteger,
        maxElements: Int
    ) {
        getContent().value?.let {
            if (it.folderExists)
                viewModelScope.launch {
                    cacheJson(getApplication<Application>().applicationContext, it)
                }
        }

        nbProcessed.getAndIncrement()
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                R.id.viewer_load,
                0,
                nbProcessed.get(),
                0,
                maxElements
            )
        )
        var idx: Int? = null
        var img: ImageFile? = null
        for ((index, image) in viewerImagesInternal.withIndex()) {
            if (image.id == identifier) {
                idx = index
                img = image
                break
            }
        }

        if (img != null && idx != null) {
            indexExtractInProgress.remove(idx)
            // Instanciate a new list to trigger an actual Adapter UI refresh every 4 iterations
            updateImgWithExtractedUri(
                img, idx, uri, 0 == nbProcessed.get() % 4 || nbProcessed.get() == maxElements
            )
        }
        dao.cleanup()
    }

    private fun updateImgWithExtractedUri(
        img: ImageFile,
        idx: Int,
        uri: Uri,
        refresh: Boolean
    ) {
        // Instanciate a new ImageFile not to modify the one used by the UI
        val extractedPic = ImageFile(img)
        extractedPic.displayUri = uri.toString()
        synchronized(viewerImagesInternal) {
            viewerImagesInternal.removeAt(idx)
            viewerImagesInternal.add(idx, extractedPic)
            Timber.v(
                "Extracting : replacing index $idx - order ${extractedPic.order} -> ${extractedPic.displayUri}"
            )
            preloadImageTypes(listOf(idx)) {
                if (refresh) viewerImages.postValue(ArrayList(viewerImagesInternal))
            }
        }
    }

    private fun onExtractionComplete(
        nbProcessed: AtomicInteger, maxElements: Int
    ) {
        Timber.d("Extracted %d files successfuly", maxElements)
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.viewer_load,
                0,
                nbProcessed.get(),
                0,
                maxElements
            )
        )
        indexExtractInProgress.clear()
        archiveExtractKillSwitch.set(false)
    }

    /**
     * Download the picture at the given index to the given folder (blocking)
     *
     * @param pageIndex    Index of the picture to download
     * @param stopDownload Switch to interrupt the download
     * @return Optional triple with
     * - The page index
     * - The Uri of the downloaded file
     *
     * The return value is empty if the download fails
     */
    private suspend fun downloadPic(
        pageIndex: Int,
        stopDownload: AtomicBoolean
    ): Pair<Int, String>? = withContext(Dispatchers.IO) {
        if (viewerImagesInternal.size <= pageIndex) return@withContext null
        val img = viewerImagesInternal[pageIndex]!!
        val content = img.content.target

        // Already downloaded
        if (img.displayUri.isNotEmpty() &&
            StorageCache.getFile(READER_CACHE, formatCacheKey(img)) != null
        ) return@withContext Pair(pageIndex, img.displayUri)

        // Run actual download
        return@withContext me.devsaki.hentoid.util.download.downloadPic(
            application,
            content,
            img,
            pageIndex,
            null,
            this@ReaderViewModel::notifyDownloadProgress
        ) { stopDownload.get() }
    }

    /**
     * Send the current book to the queue to be reparsed from scratch
     *
     * @param onError Consumer to call in case reparsing fails
     */
    fun reparseContent(onError: (Throwable) -> Unit) {
        val theContent = content.value ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = reparseFromScratch(theContent, true, updateImages = true)
                        ?: throw EmptyResultException()
                    dao.addContentToQueue(
                        c,
                        null,
                        StatusContent.SAVED,
                        QueuePosition.TOP,
                        -1,
                        null, null,
                        isQueueActive(getApplication())
                    )
                    if (Settings.isQueueAutostart) resumeQueue(getApplication())
                    dao.cleanup()
                }
            } catch (t: Throwable) {
                Timber.e(t)
                onError.invoke(t)
            }
        }
    }

    /**
     * Send the current book to the queue to be redownloaded from scratch
     *
     * @param onError Consumer to call in case reparsing fails
     */
    fun redownloadImages(onError: (Throwable) -> Unit) {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating()) onError.invoke(
                EmptyResultException(
                    getApplication<Application>().getString(R.string.redownloaded_updating_webview)
                )
            ) else onError.invoke(EmptyResultException(getApplication<Application>().getString(R.string.redownloaded_missing_webview)))
            return
        }
        val theContent = content.value ?: return
        val contentList = listOf(theContent)

        // Flag the content as "being deleted" (triggers blink animation)
        dao.updateContentsProcessedFlag(contentList, true)
        dao.cleanup()
        val targetImageStatus = StatusContent.ERROR

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentList.forEach {
                        // Non-blocking performance bottleneck; run in a dedicated worker
                        purgeContent(
                            getApplication(), it,
                            keepCover = false
                        )
                        dao.addContentToQueue(
                            it,
                            null,
                            targetImageStatus,
                            QueuePosition.TOP,
                            -1,
                            null, null,
                            isQueueActive(getApplication())
                        )
                    }
                    if (Settings.isQueueAutostart) resumeQueue(getApplication())
                    dao.cleanup()
                }
                onContentRemoved()
            } catch (t: Throwable) {
                Timber.e(t)
                onError.invoke(t)
            }
        }
    }

    /**
     * Notify the download progress of the given page
     *
     * @param progressPc % progress to display
     * @param pageIndex  Index of downloaded page
     */
    private fun notifyDownloadProgress(progressPc: Float, pageIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            doNotifyDownloadProgress(progressPc, pageIndex)
        }
    }

    /**
     * Notify the download progress of the given page
     *
     * @param progressPc % progress to display
     * @param pageIndex  Index of downloaded page
     */
    private fun doNotifyDownloadProgress(progressPc: Float, pageIndex: Int) {
        val progress = floor(progressPc.toDouble()).toInt()
        if (progress < 0) { // Error
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.FAILURE, R.id.viewer_page_download, pageIndex, 0, 100, 100
                )
            )
        } else if (progress < 100) {
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.viewer_page_download,
                    pageIndex,
                    progress,
                    0,
                    100
                )
            )
        } else {
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.COMPLETE,
                    R.id.viewer_page_download,
                    pageIndex,
                    progress,
                    0,
                    100
                )
            )
        }
    }

    /**
     * Strip all chapters from the current Content
     * NB : All images are kept; only chapters are removed
     *
     * @param onError Callback in case processing fails
     */
    fun stripChapters(onError: (Throwable) -> Unit) {
        val theContent = content.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dao.deleteChapters(theContent)
                    dao.cleanup()
                }
                // Force reload images
                loadDatabaseImages(theContent, -1)
            } catch (t: Throwable) {
                Timber.e(t)
                onError.invoke(t)
            }
        }
    }

    /**
     * Create or remove a chapter at the given position
     * - If the given position is the first page of a chapter -> remove this chapter
     * - If not, create a new chapter at this position
     *
     * @param selectedPage Position to remove or create a chapter at
     * @param onError      Callback in case processing fails
     */
    fun createRemoveChapter(selectedPage: ImageFile, onError: (Throwable) -> Unit) {
        val theContent = content.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doCreateRemoveChapter(theContent.id, selectedPage.id)
                    dao.cleanup()
                }
                // Force reload images
                loadDatabaseImages(theContent, -1)
            } catch (t: Throwable) {
                Timber.e(t)
                onError.invoke(t)
            }
        }
    }

    /**
     * Create or remove a chapter at the given position
     *   - If the given position is the first page of a chapter -> remove this chapter
     *   - If not, create a new chapter at this position
     *
     * @param contentId      ID of the corresponding content
     * @param selectedPageId ID of the page to remove or create a chapter at
     */
    private suspend fun doCreateRemoveChapter(contentId: Long, selectedPageId: Long) {
        // Work on a fresh content
        val theContent: Content =
            dao.selectContent(contentId) ?: throw IllegalArgumentException("No content found")

        val selectedPage = dao.selectImageFile(selectedPageId)
            ?: throw IllegalArgumentException("No page found")
        var selectedChapter = selectedPage.linkedChapter
        // Creation of the very first chapter of the book -> unchaptered pages are considered as "chapter 1"
        if (null == selectedChapter) {
            selectedChapter = Chapter(1, "", "$chapterStr 1", "")
            theContent.imageFiles.let { workingList ->
                selectedChapter.setImageFiles(workingList)
                // Link images the other way around so that what follows works properly
                for (img in workingList) img.setChapter(selectedChapter)
            }
            selectedChapter.setContent(theContent)
        }
        val chapterImages = selectedChapter.imageList
        require(chapterImages.isNotEmpty()) { "No images found for selection" }
        require(selectedPage.order >= 2) { "Can't create or remove chapter on first page" }

        // If we tap the 1st page of an existing chapter, it means we're removing it
        val firstChapterPic = chapterImages.filter { it.isReadable }.minByOrNull { it.order }
        val isRemoving =
            if (firstChapterPic != null) firstChapterPic.order == selectedPage.order else false

        if (isRemoving) doRemoveChapter(theContent, selectedChapter)
        else doCreateChapter(theContent, selectedPage, selectedChapter, chapterImages)

        // Rearrange all chapters

        // Work on a clean image set directly from the DAO
        // (we don't want to depend on LiveData being on time here)
        val theViewerImages = dao.selectImagesFromContent(theContent.id, true)
        // Rely on the order of pictures to get chapter in the right order
        val allChapters =
            theViewerImages.asSequence().mapNotNull { it.linkedChapter }.distinct()
                .filter { it.order > -1 }
                .toList()

        // Renumber all chapters to reflect changes
        renumberChapters(allChapters)

        // Map them to the proper content
        allChapters.forEach { it.setContent(theContent) }
        val updatedChapters = allChapters.toList()

        // Save chapters
        dao.insertChapters(updatedChapters)
        val finalContent = dao.selectContent(contentId)
        if (finalContent != null) persistJson(getApplication(), finalContent)
        dao.cleanup()
    }

    /**
     * Create a chapter at the given position, which will become the 1st page of the new chapter
     *
     * @param content        Corresponding Content
     * @param selectedPage   Position to create a new chapter at
     * @param currentChapter Current chapter at the given position
     * @param chapterImgs  Images of the current chapter at the given position
     */
    private fun doCreateChapter(
        content: Content,
        selectedPage: ImageFile,
        currentChapter: Chapter,
        chapterImgs: List<ImageFile>
    ) {
        val newChapterOrder = currentChapter.order + 1
        val newChapter = Chapter(newChapterOrder, "", "$chapterStr $newChapterOrder", "")
        newChapter.setContent(content)

        // Sort by order
        val chapterImages = chapterImgs.toList().sortedBy { it.order }

        // Split pages
        val firstPageOrder = selectedPage.order
        val lastPageOrder = chapterImages.maxOf { it.order }

        val chapCache = HashMap<Long, Chapter>()
        chapterImages.asSequence().forEach {
            if (it.order in firstPageOrder..lastPageOrder) {
                val oldChapId = it.chapterId
                val oldChapter = chapCache.get(oldChapId) ?: it.linkedChapter
                oldChapter?.let { ch ->
                    chapCache[oldChapId] = ch
                    ch.removeImageFile(it)
                }
                it.setChapter(newChapter)
                newChapter.addImageFile(it)
            }
        }

        // Save images
        dao.insertImageFiles(chapterImages)
        dao.cleanup()
    }

    fun mergeChapters(toMerge: Collection<Chapter>) {
        val contentId = content.value?.id ?: return
        var theContent: Content? = null

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Remove starting from the highest order = merge
                    val inverted = toMerge.sortedByDescending { it.order }
                    inverted.forEachIndexed { idx, ch ->
                        if (idx == inverted.size - 1) return@forEachIndexed
                        // Work on fresh ojbects updated with previous operation
                        val theChapter = dao.selectChapter(ch.id)
                            ?: throw IllegalArgumentException("No chapter found")
                        theContent = dao.selectContent(contentId)
                            ?: throw IllegalArgumentException("No content found")
                        doRemoveChapter(theContent, theChapter)
                    }
                    dao.cleanup()
                }
                // Force reload images
                theContent?.let { loadDatabaseImages(it, -1) }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Remove the given chapter
     * All pages from this chapter will be affected to the preceding chapter
     *
     * @param content       Corresponding Content
     * @param toRemove      Chapter to remove
     */
    private fun doRemoveChapter(
        content: Content, toRemove: Chapter
    ) {
        val contentChapters = content.chaptersList.sortedBy { it.order }
        val chapterImages = toRemove.imageList

        // Identify preceding chapter
        var precedingChapter: Chapter? = null
        for (c in contentChapters) {
            if (c.id == toRemove.id) break
            precedingChapter = c
        }
        if (null == precedingChapter) return

        // Pages of selected chapter will join the preceding chapter
        chapterImages.forEach { it.setChapter(precedingChapter) }
        dao.insertImageFiles(chapterImages)

        toRemove.clearImageFiles()
        dao.deleteChapter(toRemove)
    }

    fun renameChapter(chapterId: Long, newName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dao.selectChapter(chapterId)?.let { chp ->
                        chp.name = newName
                        dao.insertChapters(listOf(chp))
                    }
                    dao.cleanup()
                }
                withContext(Dispatchers.Main) {
                    reloadContent()
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    fun deleteChapters(chapterIds: List<Long>, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                deleteChapters(getApplication(), dao, chapterIds)
                withContext(Dispatchers.Main) { reloadContent() }
            } catch (t: Throwable) {
                Timber.e(t)
                onError.invoke(t)
            }
        }
    }

    fun saveChapterPositions(chapters: List<Chapter>) {
        // Use worker to reorder ImageFiles and associated files
        val builder = SplitMergeData.Builder()
        builder.setOperation(SplitMergeType.REORDER)
        builder.setChapterIds(chapters.map { it.id })
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.reorder_service.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(ReorderWorker::class.java)
                .setInputData(builder.data).build()
        )
    }

    private fun onCacheCleanup() {
        synchronized(viewerImagesInternal) {
            viewerImagesInternal.forEach {
                if ((it.isArchived || it.status == StatusContent.ONLINE)
                    && !StorageCache.peekFile(READER_CACHE, formatCacheKey(it))
                ) it.displayUri = ""
            }
        }
    }

    private fun preloadImageTypes(indexes: List<Int>, onDone: KRunnable? = null) {
        if (indexes.isEmpty()) return
        indexes.forEach {
            while (preloadKillSwitches.size >= PRELOAD_RANGE) {
                val stopPreload = preloadKillSwitches.poll()
                stopPreload?.set(true)
                Timber.d("Aborting a preload")
            }

            val stopPreload = AtomicBoolean(false)
            preloadKillSwitches.add(stopPreload)

            val img = viewerImagesInternal[it]
            if (img.imageType == ImageType.IMG_TYPE_UNSET) {
                viewModelScope.launch {
                    if (!stopPreload.get()) {
                        val uri = img.displayUri.ifBlank { img.fileUri }.toUri()
                        withContext(Dispatchers.IO) {
                            img.imageType = readImageType(application, uri)
                        }
                        Timber.d("${img.id} : Set image type to ${img.imageType}")
                        // Make image usable by reader if not set yet
                        val updatedImg = ImageFile(img)
                        if (img.displayUri.isBlank()) updatedImg.displayUri = img.fileUri
                        viewerImagesInternal[it] = updatedImg
                        onDone?.invoke()
                    }
                    preloadKillSwitches.remove(stopPreload)
                }
            }
        }
        //onDone?.invoke()
    }
}

private fun readImageType(context: Context, uri: Uri): ImageType {
    assertNonUiThread()
    if (uri == Uri.EMPTY) return ImageType.IMG_TYPE_ERROR

    try {
        getInputStream(context, uri).use { input ->
            val header = ByteArray(400)
            if (input.read(header) > 0) {
                val mime = getMimeTypeFromPictureBinary(header)
                val isAnimated = isImageAnimated(header)
                if (isAnimated) {
                    when (mime) {
                        MIME_IMAGE_PNG -> return ImageType.IMG_TYPE_APNG
                        MIME_IMAGE_WEBP -> return ImageType.IMG_TYPE_AWEBP
                        MIME_IMAGE_GIF -> return ImageType.IMG_TYPE_GIF
                        MIME_IMAGE_AVIF -> return ImageType.IMG_TYPE_AAVIF
                        MIME_VIDEO_MP4 -> return ImageType.IMG_TYPE_VIDEO
                    }
                } else {
                    when (mime) {
                        MIME_IMAGE_GIF -> return ImageType.IMG_TYPE_GIF
                        MIME_IMAGE_JXL -> return ImageType.IMG_TYPE_JXL
                        MIME_IMAGE_AVIF -> return ImageType.IMG_TYPE_AVIF
                    }
                }
                return ImageType.IMG_TYPE_OTHER
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Unable to open image file")
    }
    return ImageType.IMG_TYPE_ERROR
}