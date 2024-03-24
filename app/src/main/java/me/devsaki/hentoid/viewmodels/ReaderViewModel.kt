package me.devsaki.hentoid.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.annimon.stream.Optional
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.SEED_PAGES
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.RandomSeed
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.download.downloadToFileCached
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.UnsupportedContentException
import me.devsaki.hentoid.util.file.DiskCache
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.extractArchiveEntriesCached
import me.devsaki.hentoid.util.image.getMimeTypeFromUri
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getExtensionFromUri
import me.devsaki.hentoid.util.network.peekCookies
import me.devsaki.hentoid.widget.ContentSearchManager
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.data.DeleteData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.Queue
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.math.floor
import kotlin.math.roundToInt


class ReaderViewModel(
    application: Application, private val dao: CollectionDAO
) : AndroidViewModel(application) {
    // Collection DAO
    private val searchManager: ContentSearchManager = ContentSearchManager(dao)

    // Collection data
    private val content = MutableLiveData<Content?>() // Current content

    // Content Ids of the whole collection ordered according to current filter
    private var contentIds = mutableListOf<Long>()

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


    init {
        showFavouritesOnly.postValue(false)
        shuffled.postValue(false)
    }

    override fun onCleared() {
        dao.cleanup()
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
    fun loadContentFromId(contentId: Long, pageNumber: Int, forceImageUIReload: Boolean = false) {
        if (contentId > 0) {
            val loadedContent = dao.selectContent(contentId)
            if (loadedContent != null) {
                if (contentIds.isEmpty()) contentIds.add(contentId)
                loadContent(loadedContent, pageNumber, forceImageUIReload)
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
    fun loadContentFromSearchParams(contentId: Long, pageNumber: Int, bundle: Bundle) {
        searchManager.loadFromBundle(bundle)
        loadContentFromSearchParams(contentId, pageNumber)
    }

    /**
     * Load the given Content at the given page number using the current state of SearchManager
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     */
    private fun loadContentFromSearchParams(contentId: Long, pageNumber: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val list = searchManager.searchContentIds()
                    contentIds.clear()
                    contentIds.addAll(list)
                }
                loadContentFromId(contentId, pageNumber)
            } catch (e: Throwable) {
                Timber.w(e)
            }
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
        c.isFolderExists = false
        c.isDynamic = true
        content.postValue(c)

        if (currentImageSource != null) databaseImages.removeSource(currentImageSource!!)
        currentImageSource = dao.selectAllFavouritePagesLive()
        databaseImages.addSource(currentImageSource!!) { imgs ->
            loadImages(
                c,
                -1,
                imgs.toMutableList()
            )
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
        val pic = viewerImagesInternal.find { img -> img.id == imageId }
        pic?.let {
            startingIndex.postValue(it.displayOrder)
        }
    }

    /**
     * Process the given raw ImageFile entries to populate the viewer
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with
     * @param newImages  Images to process
     * @param forceImgUIReload  Force the reloading of all images
     */
    private fun loadImages(
        theContent: Content,
        pageNumber: Int,
        newImages: MutableList<ImageFile>,
        forceImgUIReload: Boolean = false
    ) {
        databaseImages.postValue(newImages)
        if (forceImgUIReload) newImages.forEach { it.isForceRefresh = true }

        // Don't reload from disk / archive again if the image list hasn't changed
        // e.g. page favourited
        if (!theContent.isArchive) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    processStorageImages(theContent, newImages)
                    cacheJson(getApplication<Application>().applicationContext, theContent)
                }
                processImages(theContent, -1, newImages)
            }
        } else {
            // Copy location properties of the new list on the current list
            for (i in newImages.indices) {
                val newImg = newImages[i]
                val cacheUri = DiskCache.getFile(formatCacheKey(newImg))
                if (cacheUri != null) newImg.fileUri = cacheUri.toString()
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
    private fun processStorageImages(
        theContent: Content, newImages: MutableList<ImageFile>
    ) {
        require(!theContent.isArchive) { "Content must not be an archive" }
        val missingUris = newImages.any { img: ImageFile -> img.fileUri.isEmpty() }
        var newImageFiles: List<ImageFile> = ArrayList(newImages)

        // Reattach actual files to the book's pictures if they are empty or have no URI's
        if (missingUris || newImages.isEmpty()) {
            val pictureFiles =
                ContentHelper.getPictureFilesFromContent(getApplication(), theContent)
            if (pictureFiles.isNotEmpty()) {
                if (newImages.isEmpty()) {
                    newImageFiles = ContentHelper.createImageListFromFiles(pictureFiles)
                    theContent.setImageFiles(newImageFiles)
                    dao.insertContent(theContent)
                } else {
                    // Match files for viewer display; no need to persist that
                    ContentHelper.matchFilesToImageList(pictureFiles, newImageFiles)
                }
            } else { // Try to get some from the cache
                newImageFiles.forEach {
                    val existingUri = DiskCache.getFile(formatCacheKey(it))
                    if (existingUri != null) {
                        it.fileUri = existingUri.toString()
                        it.mimeType = getMimeTypeFromUri(
                            getApplication<Application>().applicationContext, existingUri
                        )
                    }
                }
            }
        }

        // Replace initial images with updated images
        newImages.clear()
        newImages.addAll(newImageFiles)
    }

    /**
     * Callback to run when the activity is on the verge of being destroyed
     */
    fun onActivityLeave() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AchievementsManager.checkCollection(getApplication())
            }
        }
    }

    /**
     * Initialize the picture viewer using the given parameters
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with
     * @param imageFiles Pictures to process
     */
    private fun processImages(theContent: Content, pageNumber: Int, imageFiles: List<ImageFile>) {
        val shuffledVal = getShuffled().value
        sortAndSetViewerImages(imageFiles, null != shuffledVal && shuffledVal)
        if (theContent.id != loadedContentId) contentFirstLoad(theContent, pageNumber, imageFiles)
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

    /**
     * Initialize the picture viewer using the given parameters
     * (used only once per book when it is loaded for the first time)
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with
     * @param imageFiles Pictures to process
     */
    private fun contentFirstLoad(
        theContent: Content, pageNumber: Int, imageFiles: List<ImageFile>
    ) {
        var startingIndex = 0

        // Auto-restart at last read position if asked to
        if (Preferences.isReaderResumeLastLeft() && theContent.lastReadPageIndex > -1) startingIndex =
            theContent.lastReadPageIndex

        // Start at the given page number, if any
        if (pageNumber > -1) {
            imageFiles.forEachIndexed { index, it ->
                if (it.order == pageNumber) {
                    startingIndex = index + 1
                    return@forEachIndexed
                }
            }
        }

        startingIndex = adjustPageIndex(startingIndex, imageFiles)
        setViewerStartingIndex(startingIndex)

        // Init the read pages write cache
        readPageNumbers.clear()

        // Mark initial page as read
        if (startingIndex < imageFiles.size) markPageAsRead(imageFiles[startingIndex].order)
    }

    /**
     * Toggle the shuffle mode
     */
    fun toggleShuffle() {
        val shuffledVal = getShuffled().value
        var isShuffled = null != shuffledVal && shuffledVal
        isShuffled = !isShuffled
        if (isShuffled) RandomSeed.renewSeed(SEED_PAGES)
        shuffled.postValue(isShuffled)
        val imgs = databaseImages.value
        imgs?.let { sortAndSetViewerImages(it, isShuffled) }
    }

    /**
     * Sort and set the given images for the viewer
     *
     * @param images    Images to process
     * @param shuffle Trye if shuffle mode is on; false if not
     */
    private fun sortAndSetViewerImages(images: List<ImageFile>, shuffle: Boolean) {
        var imgs = images.toList()
        imgs = if (shuffle) {
            imgs.shuffled(Random(RandomSeed.getSeed(SEED_PAGES)))
        } else {
            // Sort images according to their Order; don't keep the cover thumb
            imgs.sortedBy { obj -> obj.order }
        }
        // Don't keep the cover thumb
        imgs = imgs.filter { obj -> obj.isReadable }
        val showFavouritesOnlyVal = getShowFavouritesOnly().value
        if (showFavouritesOnlyVal != null && showFavouritesOnlyVal) {
            imgs = imgs.filter { obj -> obj.isFavourite }
        }
        for (i in imgs.indices) imgs[i].displayOrder = i

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
            val oldChapters = viewerImagesInternal.map { obj -> obj.linkedChapter }
            val newChapters = imgs.map { obj -> obj.linkedChapter }.toList()
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
            viewerImages.postValue(viewerImagesInternal.toList())
        }
    }

    /**
     * Callback to run whenever a book is left (e.g. using previous/next or leaving activity)
     *
     * @param viewerIndex Viewer index of the active page when the user left the book
     */
    fun onLeaveBook(viewerIndex: Int) {
        if (Preferences.Constant.VIEWER_DELETE_ASK_BOOK == Preferences.getReaderDeleteAskMode()) Preferences.setReaderDeleteAskMode(
            Preferences.Constant.VIEWER_DELETE_ASK_AGAIN
        )
        indexDlInProgress.clear()
        indexExtractInProgress.clear()
        archiveExtractKillSwitch.set(true)

        // Don't do anything if the Content hasn't even been loaded
        if (-1L == loadedContentId) return
        val theImages = databaseImages.value
        val theContent = dao.selectContent(loadedContentId)
        if (null == theImages || null == theContent) return
        val nbReadablePages = theImages.count { obj -> obj.isReadable }
        val readThresholdPosition: Int = when (Preferences.getReaderPageReadThreshold()) {
            Preferences.Constant.VIEWER_READ_THRESHOLD_1 -> 1
            Preferences.Constant.VIEWER_READ_THRESHOLD_2 -> 2
            Preferences.Constant.VIEWER_READ_THRESHOLD_5 -> 5
            Preferences.Constant.VIEWER_READ_THRESHOLD_ALL -> nbReadablePages
            else -> nbReadablePages
        }
        val completedThresholdRatio: Float = when (Preferences.getReaderRatioCompletedThreshold()) {
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_10 -> 0.1f
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_25 -> 0.25f
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_33 -> 0.33f
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_50 -> 0.5f
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_75 -> 0.75f
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_ALL -> 1f
            Preferences.Constant.VIEWER_COMPLETED_RATIO_THRESHOLD_NONE -> 2f
            else -> 2f
        }
        val completedThresholdPosition = (completedThresholdRatio * nbReadablePages).roundToInt()
        val collectionIndex =
            viewerIndex + if (-1 == thumbIndex || thumbIndex > viewerIndex) 0 else 1
        val isLastPage = viewerIndex >= viewerImagesInternal.size - 1
        val indexToSet = if (isLastPage) 0 else collectionIndex
        val updateReads = readPageNumbers.size >= readThresholdPosition || theContent.reads > 0
        val markAsComplete = readPageNumbers.size >= completedThresholdPosition

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    doLeaveBook(
                        theContent.id, indexToSet, updateReads, markAsComplete
                    )
                } catch (t: Throwable) {
                    Timber.e(t)
                }
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
    private fun doLeaveBook(
        contentId: Long, indexToSet: Int, updateReads: Boolean, markAsCompleted: Boolean
    ) {
        Helper.assertNonUiThread()

        // Use a brand new DAO for that (the viewmodel's DAO may be in the process of being cleaned up)
        val dao: CollectionDAO = ObjectBoxDAO(getApplication<Application>())
        try {
            // Get a fresh version of current content in case it has been updated since the initial load
            // (that can be the case when viewing a book that is being downloaded)
            val savedContent = dao.selectContent(contentId) ?: return
            val theImages = savedContent.imageFiles ?: return

            // Update image read status with the cached read statuses
            val previousReadPageNumbers =
                theImages.filter { obj -> obj.isRead && obj.isReadable }
                    .map { obj -> obj.order }
                    .toSet()
            val reReadPagesNumbers = readPageNumbers.toMutableSet()
            reReadPagesNumbers.retainAll(previousReadPageNumbers)
            if (readPageNumbers.size > reReadPagesNumbers.size) {
                for (img in theImages) if (readPageNumbers.contains(img.order)) img.isRead = true
                savedContent.computeReadProgress()
            }
            if (indexToSet != savedContent.lastReadPageIndex || updateReads || readPageNumbers.size > reReadPagesNumbers.size || savedContent.isCompleted != markAsCompleted)
                ContentHelper.updateContentReadStats(
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
            searchManager.setFilterPageFavourites(targetState)
            loadContentFromSearchParams(loadedContentId, -1)
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
        val newState = !file.isFavourite
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
                withContext(Dispatchers.IO) {
                    doToggleImageFavourite(images)
                }
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
    private fun doToggleImageFavourite(images: List<ImageFile>) {
        Helper.assertNonUiThread()
        if (images.isEmpty()) return
        val theContent: Content = dao.selectContent(images[0].content.targetId) ?: return

        // We can't work on the given objects as they are tied to the UI (part of ImageFileItem)
        val dbImages = theContent.imageFiles ?: return
        for (img in images) for (dbImg in dbImages) if (img.id == dbImg.id) {
            dbImg.isFavourite = !dbImg.isFavourite
            break
        }

        // Persist in DB
        dao.insertImageFiles(dbImages)

        // Persist new values in JSON
        theContent.setImageFiles(dbImages)
        ContentHelper.persistJson(getApplication<Application>().applicationContext, theContent)
    }

    /**
     * Toggle the favourite flag of the given Content
     *
     * @param successCallback Callback to be called on success
     */
    fun toggleContentFavourite(viewerIndex: Int, successCallback: (Boolean) -> Unit) {
        val theContent = getContent().value ?: return
        val newState = !theContent.isFavourite

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doToggleContentFavourite(theContent)
                }
                reloadContent(false, viewerIndex) // Must run on the main thread
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
    private fun doToggleContentFavourite(content: Content) {
        Helper.assertNonUiThread()
        content.isFavourite = !content.isFavourite

        // Persist in DB
        dao.insertContent(content)

        // Persist new values in JSON
        ContentHelper.persistJson(getApplication<Application>().applicationContext, content)
    }

    /**
     * Set the given rating for the current content
     */
    fun setContentRating(rating: Int, successCallback: (Int) -> Unit) {
        val targetContent: Content = dao.selectContent(loadedContentId) ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    targetContent.rating = rating
                    dao.insertContent(targetContent)
                    ContentHelper.persistJson(
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
        val targetContent = dao.selectContent(loadedContentId)
        try {
            targetContent ?: throw IllegalArgumentException("Content $loadedContentId not found")

            // Unplug image source listener (avoid displaying pages as they are being deleted; it messes up with DB transactions)
            currentImageSource?.let { databaseImages.removeSource(it) }

            val builder = DeleteData.Builder()
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
                        loadImages(
                            it,
                            -1,
                            imgs.toMutableList()
                        )
                    }
                }
            }
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
        if (imageFiles.size > pageViewerIndex && pageViewerIndex > -1) deletePages(
            listOf(imageFiles[pageViewerIndex]), onError
        )
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
                withContext(Dispatchers.IO) {
                    ContentHelper.removePages(pages, dao, getApplication())
                }
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
                withContext(Dispatchers.IO) {
                    ContentHelper.setAndSaveContentCover(page, dao, getApplication())
                }
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
        if (currentContentIndex < contentIds.size - 1) {
            currentContentIndex++
            if (contentIds.isNotEmpty()) {
                onLeaveBook(viewerIndex)
                loadContentFromId(contentIds[currentContentIndex], -1)
                return true
            }
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
            if (contentIds.isNotEmpty()) {
                onLeaveBook(viewerIndex)
                loadContentFromId(contentIds[currentContentIndex], -1)
                return true
            }
        }
        return false
    }

    private fun reloadContent(forceImageUIReload: Boolean = false, viewerIndex: Int = -1) {
        loadContentFromId(contentIds[currentContentIndex], -1, forceImageUIReload)
        if (viewerIndex > -1) setViewerStartingIndex(viewerIndex)
    }

    /**
     * Load the given content at the given page number
     *
     * @param c Content to load
     * @param pageNumber Page number to start with
     */
    private fun loadContent(c: Content, pageNumber: Int, forceImageUIReload: Boolean = false) {
        Preferences.setReaderCurrentContent(c.id)
        currentContentIndex = contentIds.indexOf(c.id)
        if (-1 == currentContentIndex) currentContentIndex = 0
        c.isFirst = 0 == currentContentIndex
        c.isLast = currentContentIndex >= contentIds.size - 1
        if (null == FileHelper.getDocumentFromTreeUriString(
                getApplication(),
                c.storageUri
            )
        ) c.isFolderExists = false
        content.postValue(c)
        loadDatabaseImages(c, pageNumber, forceImageUIReload)
    }

    /**
     * Load the given Content's pictures from the database and process them, initializing the viewer to start at the given page number
     *
     * @param theContent Content to load the pictures for
     * @param pageNumber Page number to start with
     */
    private fun loadDatabaseImages(
        theContent: Content,
        pageNumber: Int,
        forceImageUIReload: Boolean = false
    ) {
        // Observe the content's images
        // NB : It has to be dynamic to be updated when viewing a book from the queue screen
        if (currentImageSource != null) databaseImages.removeSource(currentImageSource!!)
        currentImageSource = dao.selectDownloadedImagesFromContentLive(theContent.id)
        databaseImages.addSource(currentImageSource!!) { imgs ->
            loadImages(theContent, pageNumber, imgs.toMutableList(), forceImageUIReload)
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
                    theContent = dao.selectContent(loadedContentId)
                    theContent?.let {
                        it.bookPreferences = newPrefs
                        dao.insertContent(it)
                    }
                }
                reloadContent(true, viewerIndex) // Must run on the main thread
                withContext(Dispatchers.IO) {
                    // Persist in JSON
                    theContent?.let {
                        ContentHelper.persistJson(getApplication(), it)
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
    private fun cacheJson(context: Context, content: Content) {
        Helper.assertNonUiThread()
        if (content.jsonUri.isNotEmpty() || content.isArchive) return
        val folder = FileHelper.getDocumentFromTreeUriString(context, content.storageUri) ?: return
        val foundFile = FileHelper.findFile(getApplication(), folder, JSON_FILE_NAME_V2)
        if (null == foundFile) {
            Timber.e("JSON file not detected in %s", content.storageUri)
            return
        }

        // Cache the URI of the JSON to the database
        content.jsonUri = foundFile.uri.toString()
        dao.insertContent(content)
    }

    /**
     * Mark the given page number as read
     *
     * @param pageNumber Page number to mark as read
     */
    fun markPageAsRead(pageNumber: Int) {
        readPageNumbers.add(pageNumber)
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
    @Synchronized
    fun onPageChange(viewerIndex: Int, direction: Int) {
        if (viewerImagesInternal.size <= viewerIndex) return
        val theContent = getContent().value ?: return
        val isArchive = theContent.isArchive
        val picturesLeftToProcess = IntRange(0, viewerImagesInternal.size - 1).filter { i ->
            isPictureNeedsProcessing(i, viewerImagesInternal)
        }.toSet()
        if (picturesLeftToProcess.isEmpty()) return

        // Identify pages to be loaded
        val indexesToLoad: MutableList<Int> = ArrayList()
        val increment = if (direction >= 0) 1 else -1
        val quantity = if (isArchive) EXTRACT_RANGE else DOWNLOAD_RANGE
        // pageIndex at 1/3rd of the range to extract/download -> determine its bound
        val initialIndex = floor(
            Helper.coerceIn(
                1f * viewerIndex - quantity * increment / 3f,
                0f,
                (viewerImagesInternal.size - 1).toFloat()
            )
        ).toInt()
        for (i in 0 until quantity)
            if (picturesLeftToProcess.contains(initialIndex + increment * i))
                indexesToLoad.add(initialIndex + increment * i)

        // Only run extraction when there's at least 1/3rd of the extract range to fetch
        // (prevents calling extraction for one single picture at every page turn)
        var greenlight = true
        if (isArchive) {
            greenlight = indexesToLoad.size >= EXTRACT_RANGE / 3f
            if (!greenlight) {
                val from = if (increment > 0) initialIndex else 0
                val to = if (increment > 0) viewerImagesInternal.size else initialIndex + 1
                val leftToProcessDirection =
                    IntRange(from, to - 1).count { o: Int -> picturesLeftToProcess.contains(o) }
                greenlight = indexesToLoad.size == leftToProcessDirection
            }
        }
        if (indexesToLoad.isEmpty() || !greenlight) return

        // Group by archive for efficiency
        val indexesByArchive = indexesToLoad.filter { viewerImagesInternal[it].isArchived }
            .groupBy { viewerImagesInternal[it].content.target.storageUri }.toMap()
        indexesByArchive.keys.forEach {
            FileHelper.getFileFromSingleUriString(getApplication(), it)?.let { archiveFile ->
                extractPics(indexesByArchive[it]!!, archiveFile)
            }
        }

        val onlineIndexes =
            indexesToLoad.filter { viewerImagesInternal[it].status.equals(StatusContent.ONLINE) }
        downloadPics(onlineIndexes)
    }

    /**
     * Indicate if the picture at the given page index in the given list needs processing
     * (i.e. downloading or extracting)
     *
     * @param pageIndex Index to test
     * @param images    List of pictures to test against
     * @return True if the picture at the given index needs processing; false if not
     */
    private fun isPictureNeedsProcessing(pageIndex: Int, images: List<ImageFile>): Boolean {
        if (pageIndex < 0 || images.size <= pageIndex) return false
        val img = images[pageIndex]
        return ((img.status == StatusContent.ONLINE || // Image has to be downloaded
                img.isArchived) && // Image has to be extracted from an archive
                null == DiskCache.getFile(formatCacheKey(img))) // It hasn't been cached
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
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val resultOpt = downloadPic(index, stopDownload)
                        indexDlInProgress.remove(index)
                        if (resultOpt.isEmpty) { // Nothing to download
                            Timber.d("NO IMAGE FOUND AT INDEX %d", index)
                            notifyDownloadProgress(-1f, index)
                            return@withContext
                        }
                        val downloadedPageIndex = resultOpt.get().first
                        synchronized(viewerImagesInternal) {
                            if (viewerImagesInternal.size <= downloadedPageIndex) return@withContext

                            // Instanciate a new ImageFile not to modify the one used by the UI
                            val downloadedPic = ImageFile(viewerImagesInternal[downloadedPageIndex])
                            downloadedPic.fileUri = resultOpt.get().second
                            downloadedPic.mimeType = resultOpt.get().third
                            viewerImagesInternal.removeAt(downloadedPageIndex)
                            viewerImagesInternal.add(downloadedPageIndex, downloadedPic)
                            Timber.d(
                                "REPLACING INDEX %d - ORDER %d -> %s",
                                downloadedPageIndex,
                                downloadedPic.order,
                                downloadedPic.fileUri
                            )

                            // Instanciate a new list to trigger an actual Adapter UI refresh
                            viewerImages.postValue(ArrayList(viewerImagesInternal))
                        }
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
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
        indexesToLoad: List<Int>, archiveFile: DocumentFile
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doExtractPics(indexesToLoad, archiveFile)
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun doExtractPics(indexesToLoad: List<Int>, archiveFile: DocumentFile) {
        Helper.assertNonUiThread()
        // Interrupt current extracting process, if any
        if (indexExtractInProgress.isNotEmpty()) {
            archiveExtractKillSwitch.set(true)
            // Wait until extraction has actually stopped
            var remainingIterations = 15 // Timeout
            do {
                Helper.pause(500)
            } while (indexExtractInProgress.isNotEmpty() && remainingIterations-- > 0)
            if (indexExtractInProgress.isNotEmpty()) return
        }

        // Reset interrupt state to make sure extraction runs
        archiveExtractKillSwitch.set(false)

        // Build extraction instructions, ignoring already extracted items
        val extractInstructions: MutableList<Pair<String, String>> = ArrayList()
        var hasExistingUris = false
        for (index in indexesToLoad) {
            if (index < 0 || index >= viewerImagesInternal.size) continue
            val img = viewerImagesInternal[index]
            val c = img.content.target
            val identifier = formatCacheKey(img)

            val existingUri = DiskCache.getFile(identifier)
            if (existingUri != null) {
                updateImgWithExtractedUri(img, index, existingUri, false)
                hasExistingUris = true
            } else {
                extractInstructions.add(
                    Pair(
                        img.url.replace(c.storageUri + File.separator, ""), identifier
                    )
                )
                indexExtractInProgress.add(index)
            }
        }
        if (hasExistingUris) viewerImages.postValue(ArrayList(viewerImagesInternal))
        if (extractInstructions.isEmpty()) return

        Timber.d(
            "Extracting %d files starting from index %s", extractInstructions.size, indexesToLoad[0]
        )

        val nbProcessed = AtomicInteger()

        try {
            getApplication<Application>().applicationContext.extractArchiveEntriesCached(
                archiveFile.uri,
                extractInstructions,
                archiveExtractKillSwitch,
                { id, uri -> onResourceExtracted(id, uri, nbProcessed, indexesToLoad.size) },
                { onExtractionComplete(nbProcessed, indexesToLoad.size) })
        } catch (e: Exception) {
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
    }

    private fun onResourceExtracted(
        identifier: String, uri: Uri, nbProcessed: AtomicInteger, maxElements: Int
    ) {

        val c = getContent().value
        if (c != null && c.isFolderExists) cacheJson(
            getApplication<Application>().applicationContext, c
        )

        nbProcessed.getAndIncrement()
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS, R.id.viewer_load, 0, nbProcessed.get(), 0, maxElements
            )
        )
        var idx: Int? = null
        var img: ImageFile? = null
        for ((index, image) in viewerImagesInternal.withIndex()) {
            if (formatCacheKey(image) == identifier) {
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
    }

    private fun updateImgWithExtractedUri(img: ImageFile, idx: Int, uri: Uri, refresh: Boolean) {
        // Instanciate a new ImageFile not to modify the one used by the UI
        val extractedPic = ImageFile(img)
        extractedPic.fileUri = uri.toString()
        extractedPic.mimeType = getMimeTypeFromUri(
            getApplication<Application>().applicationContext, uri
        )
        synchronized(viewerImagesInternal) {
            viewerImagesInternal.removeAt(idx)
            viewerImagesInternal.add(idx, extractedPic)
            Timber.v(
                "Extracting : replacing index %d - order %d -> %s (%s)",
                idx,
                extractedPic.order,
                extractedPic.fileUri,
                extractedPic.mimeType
            )

            if (refresh) viewerImages.postValue(ArrayList(viewerImagesInternal))
        }
    }

    private fun onExtractionComplete(
        nbProcessed: AtomicInteger, maxElements: Int
    ) {
        Timber.d("Extracted %d files successfuly", maxElements)
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE, R.id.viewer_load, 0, nbProcessed.get(), 0, maxElements
            )
        )
        indexExtractInProgress.clear()
        archiveExtractKillSwitch.set(false)
    }

    /**
     * Download the picture at the given index to the given folder
     *
     * @param pageIndex    Index of the picture to download
     * @param stopDownload Switch to interrupt the download
     * @return Optional triple with
     * - The page index
     * - The Uri of the downloaded file
     * - The Mime-type of the downloaded file
     *
     * The return value is empty if the download fails
     */
    private fun downloadPic(
        pageIndex: Int, stopDownload: AtomicBoolean
    ): Optional<Triple<Int, String?, String?>> {
        Helper.assertNonUiThread()
        if (viewerImagesInternal.size <= pageIndex) return Optional.empty()
        val img = viewerImagesInternal[pageIndex]!!
        val content = img.content.target
        // Already downloaded
        if (img.fileUri.isNotEmpty() && DiskCache.getFile(formatCacheKey(img)) != null) return Optional.of(
            Triple(pageIndex, img.fileUri, img.mimeType)
        )

        // Initiate download
        try {
            val mimeType: String
            val targetFile: File

            // Prepare request headers
            val headers: MutableList<Pair<String, String>> = ArrayList()
            headers.add(
                Pair(HEADER_REFERER_KEY, content.readerUrl)
            ) // Useful for Hitomi and Toonily
            val result: Pair<Uri?, String>
            if (img.needsPageParsing()) {
                val pageUrl = fixUrl(img.pageUrl, content.site.url)
                // Get cookies from the app jar
                var cookieStr = getCookies(pageUrl)
                // If nothing found, peek from the site
                if (cookieStr.isEmpty()) cookieStr = peekCookies(pageUrl)
                if (cookieStr.isNotEmpty()) headers.add(
                    Pair(HEADER_COOKIE_KEY, cookieStr)
                )
                result = downloadPictureFromPage(
                    content, img, pageIndex, headers, stopDownload
                )
            } else {
                val imgUrl = fixUrl(img.url, content.site.url)
                // Get cookies from the app jar
                var cookieStr = getCookies(imgUrl)
                // If nothing found, peek from the site
                if (cookieStr.isEmpty()) cookieStr = peekCookies(content.galleryUrl)
                if (cookieStr.isNotEmpty()) headers.add(
                    Pair(HEADER_COOKIE_KEY, cookieStr)
                )
                result = downloadToFileCached(
                    getApplication(),
                    content.site,
                    imgUrl,
                    headers,
                    stopDownload,
                    formatCacheKey(img),
                    resourceId = pageIndex
                ) { f: Float ->
                    notifyDownloadProgress(f, pageIndex)
                }
            }

            val targetFileUri = result.first
            targetFileUri ?: throw ParseException("Resource is not available")

            targetFile = File(targetFileUri.path!!)
            mimeType = result.second

            return Optional.of(
                Triple(
                    pageIndex, Uri.fromFile(targetFile).toString(), mimeType
                )
            )
        } catch (ie: DownloadInterruptedException) {
            Timber.d("Download interrupted for pic %d", pageIndex)
        } catch (e: Exception) {
            Timber.w(e)
        }
        return Optional.empty()
    }

    /**
     * Download the picture represented by the given ImageFile to the given disk location
     *
     * @param content           Corresponding Content
     * @param img               ImageFile of the page to download
     * @param pageIndex         Index of the page to download
     * @param requestHeaders    HTTP request headers to use
     * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
     * @return Pair containing
     * - Left : Downloaded file
     * - Right : Detected mime-type of the downloaded resource
     * @throws UnsupportedContentException, IOException, LimitReachedException, EmptyResultException, DownloadInterruptedException in case something horrible happens
     */
    @Throws(
        UnsupportedContentException::class,
        IOException::class,
        LimitReachedException::class,
        EmptyResultException::class,
        DownloadInterruptedException::class
    )
    private fun downloadPictureFromPage(
        content: Content,
        img: ImageFile,
        pageIndex: Int,
        requestHeaders: List<Pair<String, String>>,
        interruptDownload: AtomicBoolean
    ): Pair<Uri?, String> {
        val site = content.site
        val pageUrl = fixUrl(img.pageUrl, site.url)
        val parser = ContentParserFactory.getImageListParser(content.site)
        val pages = parser.parseImagePage(pageUrl, requestHeaders)
        img.url = pages.first
        // Download the picture
        try {
            return downloadToFileCached(
                getApplication(),
                content.site,
                img.url,
                requestHeaders,
                interruptDownload,
                formatCacheKey(img),
                resourceId = pageIndex
            ) { f: Float ->
                notifyDownloadProgress(f, pageIndex)
            }
        } catch (e: IOException) {
            if (pages.second != null) Timber.d("First download failed; trying backup URL") else throw e
        }
        // Trying with backup URL
        img.url = pages.second
        return downloadToFileCached(
            getApplication(),
            content.site,
            img.url,
            requestHeaders,
            interruptDownload,
            formatCacheKey(img),
            resourceId = pageIndex
        ) { f: Float ->
            notifyDownloadProgress(f, pageIndex)
        }
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
                    val c = ContentHelper.reparseFromScratch(theContent)
                    if (c.isEmpty) throw EmptyResultException()
                    dao.addContentToQueue(
                        c.get(),
                        null,
                        StatusContent.SAVED,
                        ContentHelper.QueuePosition.TOP,
                        -1,
                        null,
                        isQueueActive(getApplication())
                    )
                    if (Preferences.isQueueAutostart()) resumeQueue(getApplication())
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
        for (c in contentList) dao.updateContentProcessedFlag(c.id, true)
        val targetImageStatus = StatusContent.ERROR

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentList.forEach {
                        // Non-blocking performance bottleneck; run in a dedicated worker
                        ContentHelper.purgeContent(getApplication(), it, false, true)
                        dao.addContentToQueue(
                            it,
                            null,
                            targetImageStatus,
                            ContentHelper.QueuePosition.TOP,
                            -1,
                            null,
                            isQueueActive(getApplication())
                        )
                    }
                    if (Preferences.isQueueAutostart()) resumeQueue(getApplication())
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                doNotifyDownloadProgress(progressPc, pageIndex)
            }
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
     * * - If the given position is the first page of a chapter -> remove this chapter
     * * - If not, create a new chapter at this position
     *
     * @param contentId      ID of the corresponding content
     * @param selectedPageId ID of the page to remove or create a chapter at
     */
    private fun doCreateRemoveChapter(contentId: Long, selectedPageId: Long) {
        Helper.assertNonUiThread()
        val chapterStr = getApplication<Application>().getString(R.string.gallery_chapter_prefix)
        if (null == VANILLA_CHAPTERNAME_PATTERN) VANILLA_CHAPTERNAME_PATTERN = Pattern.compile(
            "$chapterStr [0-9]+"
        )
        // Work on a fresh content
        val theContent: Content =
            dao.selectContent(contentId) ?: throw IllegalArgumentException("No content found")

        val selectedPage =
            dao.selectImageFile(selectedPageId) ?: throw IllegalArgumentException("No page found")
        var currentChapter = selectedPage.linkedChapter
        // Creation of the very first chapter of the book -> unchaptered pages are considered as "chapter 1"
        if (null == currentChapter) {
            currentChapter = Chapter(1, "", "$chapterStr 1")
            val workingList: List<ImageFile>? = theContent.imageFiles
            if (workingList != null) {
                currentChapter.setImageFiles(workingList)
                // Link images the other way around so that what follows works properly
                for (img in workingList) img.setChapter(currentChapter)
            }
            currentChapter.setContent(theContent)
        }
        val chapterImages: List<ImageFile>? = currentChapter.imageFiles
        require(!chapterImages.isNullOrEmpty()) { "No images found for selection" }
        require(selectedPage.order >= 2) { "Can't create or remove chapter on first page" }

        // If we tap the 1st page of an existing chapter, it means we're removing it
        val firstChapterPic =
            chapterImages.sortedBy { obj: ImageFile -> obj.order }.firstOrNull()
        val isRemoving =
            if (firstChapterPic != null) firstChapterPic.order.toInt() == selectedPage.order.toInt() else false

        if (isRemoving) doRemoveChapter(theContent, currentChapter, chapterImages)
        else doCreateChapter(theContent, selectedPage, currentChapter, chapterImages)

        // Rearrange all chapters

        // Work on a clean image set directly from the DAO
        // (we don't want to depend on LiveData being on time here)
        val theViewerImages = dao.selectDownloadedImagesFromContent(theContent.id)
        // Rely on the order of pictures to get chapter in the right order
        val allChapters =
            theViewerImages.asSequence().map { obj -> obj.linkedChapter }.distinct().filterNotNull()
                .filter { c -> c.order > -1 }.toList()

        // Renumber all chapters to reflect changes
        var order = 1
        val updatedChapters: MutableList<Chapter> = ArrayList()
        for (c in allChapters) {
            // Update names with the default "Chapter x" naming
            if (VANILLA_CHAPTERNAME_PATTERN!!.matcher(c.name).matches()) c.name =
                "$chapterStr $order"
            // Update order
            c.order = order++
            c.setContent(theContent)
            updatedChapters.add(c)
        }

        // Save chapters
        dao.insertChapters(updatedChapters)
        val finalContent = dao.selectContent(contentId)
        if (finalContent != null) ContentHelper.persistJson(getApplication(), finalContent)
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
        var chapterImages = chapterImgs
        val newChapterOrder = currentChapter.order + 1
        val newChapter = Chapter(
            newChapterOrder,
            "",
            getApplication<Application>().getString(R.string.gallery_chapter_prefix) + " " + newChapterOrder
        )
        newChapter.setContent(content)

        // Sort by order
        chapterImages = chapterImages.sortedBy { obj: ImageFile -> obj.order }

        // Split pages
        val firstPageOrder = selectedPage.order
        val lastPageOrder = chapterImages[chapterImages.size - 1].order
        for (img in chapterImages) if (img.order in firstPageOrder..lastPageOrder) {
            val oldChapter = img.linkedChapter
            oldChapter?.removeImageFile(img)
            img.setChapter(newChapter)
            newChapter.addImageFile(img)
        }

        // Save images
        dao.insertImageFiles(chapterImages)
    }

    /**
     * Remove the given chapter
     * All pages from this chapter will be affected to the preceding chapter
     *
     * @param content       Corresponding Content
     * @param toRemove      Chapter to remove
     * @param chapterImages Images of the chapter to remove
     */
    private fun doRemoveChapter(
        content: Content, toRemove: Chapter, chapterImages: List<ImageFile>
    ) {
        var contentChapters: List<Chapter> = content.chapters ?: return
        contentChapters = contentChapters.sortedBy { obj: Chapter -> obj.order }
        val removeOrder = toRemove.order

        // Identify preceding chapter
        var precedingChapter: Chapter? = null
        for (c in contentChapters) {
            if (c.order == removeOrder) break
            precedingChapter = c
        }

        // Pages of selected chapter will join the preceding chapter
        for (img in chapterImages) img.setChapter(precedingChapter)
        dao.insertImageFiles(chapterImages)
        dao.deleteChapter(toRemove)
    }

    fun saveChapterPositions(chapters: List<Chapter>) {
        val theContent = content.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doSaveChapterPositions(theContent.id, chapters.map { c -> c.id })
                }
                withContext(Dispatchers.Main) {
                    reloadContent()
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun doSaveChapterPositions(contentId: Long, newChapterOrder: List<Long>) {
        Helper.assertNonUiThread()
        val chapterStr = getApplication<Application>().getString(R.string.gallery_chapter_prefix)
        if (null == VANILLA_CHAPTERNAME_PATTERN) VANILLA_CHAPTERNAME_PATTERN = Pattern.compile(
            "$chapterStr [0-9]+"
        )
        var chapters = dao.selectChapters(contentId)
        require(chapters.isNotEmpty()) { "No chapters found" }

        // Reorder chapters according to target order
        val orderById = newChapterOrder.withIndex().associate { (index, it) -> it to index }
        chapters = chapters.sortedBy { orderById[it.id] }.toMutableList()

        // Renumber all chapters and update the DB
        chapters.forEachIndexed { index, c ->
            // Update names with the default "Chapter x" naming
            if (VANILLA_CHAPTERNAME_PATTERN!!.matcher(c.name).matches()) c.name =
                "$chapterStr " + (index + 1)
            // Update order
            c.order = index + 1
        }
        dao.insertChapters(chapters)

        // Renumber all readable images
        val orderedImages =
            chapters.mapNotNull { ch -> ch.imageFiles }.flatMap { imgLst -> imgLst.toList() }
                .filter { img -> img.isReadable }
        require(orderedImages.isNotEmpty()) { "No images found" }

        // Keep existing formatting
        val nbMaxDigits = orderedImages[orderedImages.size - 1].name.length
        val fileNames = HashMap<String, Pair<String, ImageFile>>()
        orderedImages.forEachIndexed { index, img ->
            img.order = index + 1
            img.computeName(nbMaxDigits)
            fileNames[img.fileUri] = Pair(img.name + "." + getExtensionFromUri(img.fileUri), img)
        }

        // == Compute file swaps

        //  Key = Source file URI
        //  Value
        //      first = Source file URI
        //      second = Target file URI
        //      third = Target ImageFile
        val swaps = HashMap<Uri, Triple<Uri, Uri, ImageFile>>()
        val renames = ArrayList<Triple<DocumentFile, String, ImageFile>>()
        val parentFolder = FileHelper.getDocumentFromTreeUriString(
            getApplication(), chapters[0].content.target.storageUri
        )
        if (parentFolder != null) {
            val contentFiles = FileHelper.listFiles(getApplication(), parentFolder, null)
            for (doc in contentFiles) {
                val newName = fileNames[doc.uri.toString()]
                if (newName != null) {
                    val duplicate = contentFiles.firstOrNull { f -> f.name.equals(newName.first) }
                    if (duplicate != null && duplicate.uri != doc.uri) swaps[duplicate.uri] =
                        Triple(duplicate.uri, doc.uri, newName.second)
                    else if (newName.first != doc.name) renames.add(
                        Triple(
                            doc, newName.first, newName.second
                        )
                    )
                }
            }
        }

        // Groups swaps into permutation groups
        val permutationGroups: List<List<Triple<Uri, Uri, ImageFile>>> =
            buildPermutationGroups(swaps)

        val nbTasks = swaps.size + renames.size
        var nbProcessedTasks = 1

        // Perform swaps by exchanging file content
        // NB : "thanks to" SAF; this works faster than renaming the files
        permutationGroups.forEach { tasks ->
            var firstFileContent: ByteArray
            FileHelper.getInputStream(getApplication(), tasks[0].first).use {
                firstFileContent = it.readBytes()
            }
            tasks.forEachIndexed { index, task ->
                if (index < tasks.size - 1) {
                    FileHelper.getOutputStream(getApplication(), task.first).use { os ->
                        os?.let {
                            FileHelper.getInputStream(getApplication(), task.second).use { input ->
                                Helper.copy(input, it)
                            }
                        }
                    }
                    task.third.fileUri = task.first.toString()
                    swaps.remove(task.first)
                } else { // Last task
                    FileHelper.getOutputStream(getApplication(), task.first).use { os ->
                        os?.write(firstFileContent)
                    }
                    task.third.fileUri = task.first.toString()
                }
            }
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.generic_progress,
                    0,
                    nbProcessedTasks++,
                    0,
                    nbTasks
                )
            )
        }

        // Perform renames
        renames.forEach { task ->
            task.first.renameTo(task.second)
            task.third.fileUri = task.first.uri.toString()
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.generic_progress,
                    0,
                    nbProcessedTasks++,
                    0,
                    nbTasks
                )
            )
        }

        // Finalize
        dao.insertImageFiles(orderedImages)
        val finalContent = dao.selectContent(contentId)
        if (finalContent != null) ContentHelper.persistJson(getApplication(), finalContent)
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE, R.id.generic_progress, 0, nbTasks, 0, nbTasks
            )
        )

        // Reset Glide cache as it gets confused by the swapping
        Glide.get(getApplication()).clearDiskCache()
    }

    private fun buildPermutationGroups(swaps: HashMap<Uri, Triple<Uri, Uri, ImageFile>>): List<List<Triple<Uri, Uri, ImageFile>>> {
        val result: MutableList<List<Triple<Uri, Uri, ImageFile>>> = ArrayList()

        if (swaps.isNotEmpty()) {
            val processedKeys: MutableSet<Uri> = HashSet()
            var currentGroup: MutableList<Triple<Uri, Uri, ImageFile>> = ArrayList()
            var leftToProcess = swaps.size

            var task = swaps.values.first()
            while (leftToProcess > 0) {
                processedKeys.add(task.first)
                currentGroup.add(task)
                leftToProcess--
                if (swaps.containsKey(task.second) && !processedKeys.contains(task.second)) {
                    task = swaps[task.second]!!
                } else {
                    result.add(currentGroup)
                    currentGroup = ArrayList()
                    if (leftToProcess > 0) {
                        val nextKey = swaps.keys.find { k -> !processedKeys.contains(k) }!!
                        task = swaps[nextKey]!!
                    }
                }
            }
        }
        return result.toList()
    }

    private fun formatCacheKey(img: ImageFile): String {
        return "img" + img.id
    }

    companion object {
        const val DOWNLOAD_RANGE = 6 // Sequential download; not concurrent
        const val EXTRACT_RANGE = 35

        private var VANILLA_CHAPTERNAME_PATTERN: Pattern? = null
    }
}