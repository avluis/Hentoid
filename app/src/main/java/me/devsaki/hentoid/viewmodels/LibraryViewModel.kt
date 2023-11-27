package me.devsaki.hentoid.viewmodels

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.paging.PagedList
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.annimon.stream.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.SEED_CONTENT
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.GroupHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.RandomSeed
import me.devsaki.hentoid.util.SearchHelper.AdvancedSearchCriteria
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.widget.ContentSearchManager
import me.devsaki.hentoid.widget.GroupSearchManager
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.UpdateJsonWorker
import me.devsaki.hentoid.workers.data.DeleteData
import me.devsaki.hentoid.workers.data.UpdateJsonData
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.Pair
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.security.InvalidParameterException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.log10

class LibraryViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    // Search managers
    private val contentSearchManager: ContentSearchManager = ContentSearchManager(dao)
    private val groupSearchManager: GroupSearchManager = GroupSearchManager(dao)

    // Cleanup for all work observers
    private val workObservers: MutableList<Pair<UUID, Observer<WorkInfo>>> = ArrayList()

    // Content data
    private var currentSource: LiveData<PagedList<Content>>? = null
    val totalContent: LiveData<Int> = dao.countAllBooksLive()
    val libraryPaged = MediatorLiveData<PagedList<Content>>()
    val contentSearchBundle = MutableLiveData<Bundle>()

    // Groups data
    val group = MutableLiveData<Group>()
    private var currentGroupsSource: LiveData<List<Group>>? = null
    val groups = MediatorLiveData<List<Group>>()
    private var currentGroupsTotalSource: LiveData<List<Group>>? = null
    private val currentGroupTotal = MediatorLiveData<Int>()

    // True if there's at least one existing custom group; false instead
    val isCustomGroupingAvailable = MutableLiveData<Boolean>()

    // True if there's at least one existing dynamic group; false instead
    val isDynamicGroupingAvailable = MutableLiveData<Boolean>()

    val groupSearchBundle = MutableLiveData<Bundle>()

    // Other data
    val searchRecords: LiveData<List<SearchRecord>> = dao.selectSearchRecordsLive()
    val totalQueue: LiveData<Int> = dao.countAllQueueBooksLive()
    val favPages: LiveData<Int> = dao.countAllFavouritePagesLive()

    // Updated whenever a new Contentsearch is performed
    val newContentSearch = MediatorLiveData<Boolean>()


    init {
        refreshAvailableGroupings()
    }

    fun onSaveState(outState: Bundle) {
        contentSearchManager.saveToBundle(outState)
        groupSearchManager.saveToBundle(outState)
    }

    fun onRestoreState(savedState: Bundle?) {
        if (savedState == null) return
        contentSearchManager.loadFromBundle(savedState)
        groupSearchManager.loadFromBundle(savedState)
    }

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
        if (workObservers.isEmpty()) {
            val workManager = WorkManager.getInstance(getApplication())
            for (info in workObservers) workManager.getWorkInfoByIdLiveData(info.left)
                .removeObserver(info.right)
        }
    }

    fun getTotalGroup(): LiveData<Int> {
        return currentGroupTotal
    }


    // =========================
    // ========= LIBRARY ACTIONS
    // =========================

    // =========================
    // ========= LIBRARY ACTIONS
    // =========================
    /**
     * Perform a new library search
     */
    private fun doSearchContent() {
        // Update search properties set directly through Preferences
        Timber.v(">> doSearchContent")
        contentSearchManager.setContentSortField(Preferences.getContentSortField())
        contentSearchManager.setContentSortDesc(Preferences.isContentSortDesc())
        if (Preferences.getGroupingDisplay() == Grouping.FLAT) contentSearchManager.setGroup(null)
        currentSource?.let {
            libraryPaged.removeSource(it)
        }
        currentSource = contentSearchManager.getLibrary()
        currentSource?.let {
            libraryPaged.addSource(it) { value -> libraryPaged.setValue(value) }
        }
        contentSearchBundle.postValue(contentSearchManager.toBundle())
    }

    /**
     * Perform a new content universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    fun searchContentUniversal(query: String) {
        // If user searches in main toolbar, universal search takes over advanced search
        contentSearchManager.clearSelectedSearchTags()
        contentSearchManager.setLocation(ContentHelper.Location.ANY)
        contentSearchManager.setContentType(ContentHelper.Type.ANY)
        contentSearchManager.setQuery(query)
        newContentSearch.value = true
        if (query.isNotEmpty()) {
            val searchUri =
                buildSearchUri(null, query, ContentHelper.Location.ANY, ContentHelper.Type.ANY)
            dao.insertSearchRecord(SearchRecord.fromContentUniversalSearch(searchUri), 10)
        }
        doSearchContent()
    }

    /**
     * Perform a new content search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param metadata Metadata to use for the search
     */
    fun searchContent(query: String, metadata: AdvancedSearchCriteria, searchUri: Uri) {
        contentSearchManager.setQuery(query)
        contentSearchManager.setTags(metadata.attributes)
        contentSearchManager.setLocation(metadata.location)
        contentSearchManager.setContentType(metadata.contentType)
        newContentSearch.value = true
        if (!metadata.isEmpty()) dao.insertSearchRecord(
            SearchRecord.fromContentAdvancedSearch(
                searchUri,
                metadata.toString(getApplication())
            ), 10
        )
        doSearchContent()
    }

    fun clearContent() {
        currentSource?.let {
            libraryPaged.removeSource(it)
        }
        currentSource = dao.selectNoContent()
        currentSource?.let {
            libraryPaged.addSource(it) { value -> libraryPaged.setValue(value) }
        }
    }

    fun searchGroup() {
        doSearchGroup()
    }

    private fun doSearchGroup() {
        Timber.v(">> doSearchGroup")
        // Update search properties set directly through Preferences
        groupSearchManager.setSortField(Preferences.getGroupSortField())
        groupSearchManager.setSortDesc(Preferences.isGroupSortDesc())
        groupSearchManager.setGrouping(Preferences.getGroupingDisplay())
        groupSearchManager.setArtistGroupVisibility(Preferences.getArtistGroupVisibility())
        currentGroupsSource?.let { groups.removeSource(it) }
        currentGroupsSource = groupSearchManager.getGroups()
        currentGroupsSource?.let { groups.addSource(it) { value -> groups.setValue(value) } }

        currentGroupsTotalSource?.let { currentGroupTotal.removeSource(it) }
        currentGroupsTotalSource = groupSearchManager.getAllGroups()
        currentGroupsTotalSource?.let {
            currentGroupTotal.addSource(it) { list -> currentGroupTotal.postValue(list.size) }
        }

        groupSearchBundle.postValue(groupSearchManager.toBundle())
        refreshAvailableGroupings()
    }

    fun refreshAvailableGroupings() {
        isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0)
        isDynamicGroupingAvailable.postValue(dao.countGroupsFor(Grouping.DYNAMIC) > 0)
    }

    /**
     * Toggle the completed filter
     */
    fun setCompletedFilter(value: Boolean) {
        contentSearchManager.setFilterBookCompleted(value)
        newContentSearch.value = true
        doSearchContent()
    }

    /**
     * Toggle the "not completed" filter
     */
    fun setNotCompletedFilter(value: Boolean) {
        contentSearchManager.setFilterBookNotCompleted(value)
        newContentSearch.value = true
        doSearchContent()
    }

    /**
     * Toggle the books favourite filter
     */
    fun setContentFavouriteFilter(value: Boolean) {
        contentSearchManager.setFilterBookFavourites(value)
        newContentSearch.value = true
        doSearchContent()
    }

    /**
     * Toggle the books non-favourite filter
     */
    fun setContentNonFavouriteFilter(value: Boolean) {
        contentSearchManager.setFilterBookNonFavourites(value)
        newContentSearch.value = true
        doSearchContent()
    }

    /**
     * Toggle the groups favourite filter
     */
    fun setGroupFavouriteFilter(value: Boolean) {
        groupSearchManager.setFilterFavourites(value)
        doSearchGroup()
    }

    /**
     * Toggle the groups non-favourite filter
     */
    fun setGroupNonFavouriteFilter(value: Boolean) {
        groupSearchManager.setFilterNonFavourites(value)
        doSearchGroup()
    }

    /**
     * Toggle the books rating filter
     */
    fun setContentRatingFilter(value: Int) {
        contentSearchManager.setFilterRating(value)
        newContentSearch.value = true
        doSearchContent()
    }

    /**
     * Toggle the groups rating filter
     */
    fun setGroupRatingFilter(value: Int) {
        groupSearchManager.setFilterRating(value)
        doSearchGroup()
    }


    fun setGroupQuery(value: String) {
        groupSearchManager.setQuery(value)
        doSearchGroup()
    }

    fun setGrouping(groupingId: Int) {
        val currentGrouping = Preferences.getGroupingDisplay().id
        if (groupingId != currentGrouping) {
            Preferences.setGroupingDisplay(groupingId)
            if (groupingId == Grouping.FLAT.id) doSearchContent()
            doSearchGroup()
        }
    }

    fun clearGroupFilters() {
        groupSearchManager.clearFilters()
        doSearchGroup()
    }

    fun clearContentFilters() {
        contentSearchManager.clearFilters()
        doSearchContent()
    }

    /**
     * Set Content paging mode (endless or paged)
     */
    fun setContentPagingMethod(isEndless: Boolean) {
        contentSearchManager.setLoadAll(!isEndless)
        newContentSearch.value = true
        doSearchContent()
    }

    fun searchContent() {
        newContentSearch.value = true
        doSearchContent()
    }

    fun setGroup(group: Group, forceRefresh: Boolean) {
        val currentGroup = this.group.value
        if (!forceRefresh && group == currentGroup) return

        // Reset content sorting to TITLE when reaching the Ungrouped group with CUSTOM sorting (can't work)
        if (!group.grouping.canReorderBooks || group.grouping == Grouping.CUSTOM && 1 == group.getSubtype()) Preferences.setContentSortField(
            Preferences.Constant.ORDER_FIELD_TITLE
        )
        this.group.postValue(group)
        contentSearchManager.setGroup(group)
        newContentSearch.value = true
        // Don't search now as the UI will inevitably search as well upon switching to books view
        // TODO only useful when browsing custom groups ?
        doSearchContent()
    }

    // =========================
    // ========= CONTENT ACTIONS
    // =========================
    fun toggleContentCompleted(content: List<Content>, onSuccess: Runnable) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    content.forEach { doToggleContentCompleted(it.id) }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            onSuccess.run()
        }
    }

    /**
     * Toggle the "completed" state of the given content
     *
     * @param contentId ID of the content whose completed state to toggle
     */
    private fun doToggleContentCompleted(contentId: Long) {
        Helper.assertNonUiThread()

        // Check if given content still exists in DB
        val theContent = dao.selectContent(contentId)
        if (theContent != null) {
            if (theContent.isBeingProcessed) return
            theContent.isCompleted = !theContent.isCompleted
            ContentHelper.persistJson(getApplication(), theContent)
            dao.insertContentCore(theContent)
            return
        }
        throw InvalidParameterException("Invalid ContentId : $contentId")
    }

    fun resetReadStats(content: List<Content>, onSuccess: Runnable) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    content.forEach { doResetReadStats(it.id) }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            onSuccess.run()
        }
    }

    /**
     * Reset read stats of the given content
     *
     * @param contentId ID of the content whose read stats to reset
     */
    private fun doResetReadStats(contentId: Long) {
        Helper.assertNonUiThread()

        // Check if given content still exists in DB
        val theContent = dao.selectContent(contentId)
        if (theContent != null) {
            if (theContent.isBeingProcessed) return
            theContent.reads = 0
            theContent.readPagesCount = 0
            theContent.lastReadPageIndex = 0
            theContent.lastReadDate = 0
            val imgs: List<ImageFile>? = theContent.imageFiles
            if (imgs != null) {
                for (img in imgs) img.isRead = false
                dao.insertImageFiles(imgs)
            }
            ContentHelper.persistJson(getApplication(), theContent)
            dao.insertContentCore(theContent)
            return
        }
        throw InvalidParameterException("Invalid ContentId : $contentId")
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param content Content whose favourite state to toggle
     */
    fun toggleContentFavourite(content: Content, onSuccess: Runnable) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    doToggleContentFavourite(content.id)
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            onSuccess.run()
        }
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param contentId ID of the content whose favourite state to toggle
     * @return Resulting content
     */
    private fun doToggleContentFavourite(contentId: Long): Content {
        Helper.assertNonUiThread()

        // Check if given content still exists in DB
        val theContent = dao.selectContent(contentId)
        if (theContent != null) {
            theContent.isFavourite = !theContent.isFavourite
            ContentHelper.persistJson(getApplication(), theContent)
            dao.insertContent(theContent)
            return theContent
        }
        throw InvalidParameterException("Invalid ContentId : $contentId")
    }

    /**
     * Set the rating to the given value for the given content IDs
     *
     * @param contentIds   Content IDs to set the rating for
     * @param targetRating Rating to set
     * @param onSuccess    Runnable to call if the operation succeeds
     */
    fun rateContents(contentIds: List<Long>, targetRating: Int, onSuccess: Runnable) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    contentIds.forEach {
                        doRateContent(it, targetRating)
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
            onSuccess.run()
        }
    }

    /**
     * Set the rating to the given value for the given content ID
     *
     * @param contentId    Content ID to set the rating for
     * @param targetRating Rating to set
     */
    private fun doRateContent(contentId: Long, targetRating: Int): Content {
        Helper.assertNonUiThread()

        // Check if given content still exists in DB
        val theContent = dao.selectContent(contentId)
        if (theContent != null) {
            theContent.rating = targetRating
            ContentHelper.persistJson(getApplication(), theContent)
            dao.insertContent(theContent)
            return theContent
        }
        throw InvalidParameterException("Invalid ContentId : $contentId")
    }

    /**
     * General purpose download/redownload
     * @reparseContent : True to reparse Content metadata from the site
     * @reparseImages : True to reparse and redownload images from the site
     */
    fun downloadContent(
        contentList: List<Content>,
        reparseContent: Boolean,
        reparseImages: Boolean,
        position: Int,
        onSuccess: Consumer<Int>,
        onError: Consumer<Throwable>
    ) {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating()) onError.invoke(
                EmptyResultException(
                    getApplication<Application>().getString(R.string.download_updating_webview)
                )
            ) else onError.invoke(EmptyResultException(getApplication<Application>().getString(R.string.download_missing_webview)))
            return
        }

        // Flag contents as "being processed" (triggers blink animation)
        for (c in contentList) dao.updateContentProcessedFlag(c.id, true)

        val sourceImageStatus = if (reparseImages) null else StatusContent.ERROR
        val targetImageStatus =
            if (!reparseImages) null else if (reparseContent) StatusContent.ERROR else StatusContent.SAVED

        val nbErrors = AtomicInteger(0)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentList.forEach { c ->
                        var res = Optional.of(c)
                        var areModifiedChapters = false

                        // Merged books
                        val chaps = c.chaptersList.toMutableList()
                        if (c.isManuallyMerged && chaps.isNotEmpty()) {
                            // Reparse main book from scratch if images are KO
                            if (reparseContent || !ContentHelper.isDownloadable(c)) {
                                if (!reparseContent) Timber.d("Pages unreachable; reparsing content")
                                // Reparse content itself
                                res = ContentHelper.reparseFromScratch(c)
                            }

                            // Reparse chapters from scratch if images are KO
                            chaps.forEachIndexed { idx, ch ->
                                if (res.isPresent) {
                                    if (reparseContent || !ContentHelper.isDownloadable(ch)) {
                                        if (!reparseContent) Timber.d("Pages unreachable; reparsing chapter $idx")
                                        if (ContentHelper.parseFromScratch(ch.url).isPresent) {
                                            // Flagging all pics as ERROR; will be reparsed by the downloader
                                            ch.imageList.forEach { img ->
                                                img.status = StatusContent.ERROR
                                            }
                                            areModifiedChapters = true
                                        } else res = Optional.empty()
                                    }
                                }
                            }
                            if (res.isPresent) res.get().setChapters(chaps)
                        } else { // Classic content
                            if (reparseContent || !ContentHelper.isDownloadable(c)) {
                                Timber.d("Pages unreachable; reparsing content")
                                // Reparse content itself
                                res = ContentHelper.reparseFromScratch(c)
                            }
                        }

                        if (res.isPresent) {
                            res.get().downloadMode = Content.DownloadMode.DOWNLOAD
                            if (areModifiedChapters) dao.insertChapters(res.get().chaptersList)

                            // Non-blocking performance bottleneck; run in a dedicated worker
                            if (reparseImages) ContentHelper.purgeContent(
                                getApplication(),
                                res.get(),
                                false,
                                true
                            )
                            dao.addContentToQueue(
                                res.get(), sourceImageStatus, targetImageStatus, position, -1, null,
                                isQueueActive(getApplication())
                            )
                        } else {
                            dao.updateContentProcessedFlag(c.id, false)
                            nbErrors.incrementAndGet()
                            onError.invoke(
                                if (c.isManuallyMerged && chaps.isNotEmpty()) EmptyResultException(
                                    getApplication<Application>().getString(R.string.download_canceled_merged)
                                )
                                else EmptyResultException(
                                    getApplication<Application>().getString(R.string.download_canceled)
                                )
                            )
                        }
                    } // Each content
                }
                if (Preferences.isQueueAutostart()) resumeQueue(getApplication())
                onSuccess.invoke(contentList.size - nbErrors.get())
            } catch (t: Throwable) {
                onError.invoke(t)
            }
        }
    }

    fun streamContent(
        contentList: List<Content>,
        onError: Consumer<Throwable>
    ) {
        if (!WebkitPackageHelper.getWebViewAvailable()) {
            if (WebkitPackageHelper.getWebViewUpdating()) onError.invoke(
                EmptyResultException(
                    getApplication<Application>().getString(R.string.stream_updating_webview)
                )
            ) else onError.invoke(EmptyResultException(getApplication<Application>().getString(R.string.stream_missing_webview)))
            return
        }

        // Flag the content as "being deleted" (triggers blink animation)
        for (c in contentList) dao.updateContentProcessedFlag(c.id, true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentList.forEach { c ->
                        Timber.d("Checking pages availability")
                        // Reparse content from scratch if images KO
                        val res = if (!ContentHelper.isDownloadable(c)) {
                            Timber.d("Pages unreachable; reparsing content")
                            // Reparse content itself
                            val newContent = ContentHelper.reparseFromScratch(c)
                            if (newContent.isEmpty) {
                                dao.updateContentProcessedFlag(c.id, false)
                                newContent
                            } else {
                                val reparsedContent = newContent.get()
                                // Reparse pages
                                val newImages =
                                    ContentHelper.fetchImageURLs(
                                        reparsedContent,
                                        StatusContent.ONLINE
                                    )
                                reparsedContent.setImageFiles(newImages)
                                // Associate new pages' cover with current cover file (that won't be deleted)
                                reparsedContent.cover.setStatus(StatusContent.DOWNLOADED).fileUri =
                                    c.cover.fileUri
                                // Save everything
                                dao.replaceImageList(reparsedContent.id, newImages)
                                Optional.of<Content>(reparsedContent)
                            }
                        } else Optional.of<Content>(c)

                        if (res.isPresent) {
                            dao.selectContent(res.get().id)?.let {
                                // Non-blocking performance bottleneck; scheduled in a dedicated worker
                                ContentHelper.purgeContent(
                                    getApplication(),
                                    res.get(),
                                    true,
                                    true
                                )
                                it.downloadMode = Content.DownloadMode.STREAM
                                val imgs: List<ImageFile> = it.imageList
                                for (img in imgs) {
                                    img.fileUri = ""
                                    img.size = 0
                                    img.status = StatusContent.ONLINE
                                }
                                dao.insertImageFiles(imgs)
                                it.forceSize(0)
                                it.setIsBeingProcessed(false)
                                dao.insertContent(it)
                                ContentHelper.updateJson(getApplication(), it)
                            }
                        } else {
                            onError.invoke(
                                EmptyResultException(
                                    getApplication<Application>().getString(R.string.stream_canceled)
                                )
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                onError.invoke(t)
            }
        }
    }

    /**
     * Delete the given list of content
     *
     * @param contents List of content to be deleted
     */
    fun deleteItems(
        contents: List<Content>,
        groups: List<Group>,
        deleteGroupsOnly: Boolean,
        onSuccess: Runnable?
    ) {
        val builder = DeleteData.Builder()
        if (contents.isNotEmpty()) builder.setContentIds(contents.map { c -> c.id })
        if (groups.isNotEmpty()) builder.setGroupIds(groups.map { g -> g.getId() })
        builder.setDeleteGroupsOnly(deleteGroupsOnly)
        val workManager = WorkManager.getInstance(getApplication())
        val request: WorkRequest =
            OneTimeWorkRequest.Builder(DeleteWorker::class.java).setInputData(builder.data).build()
        workManager.enqueue(request)
        val workInfoObserver =
            Observer { workInfo: WorkInfo ->
                if (workInfo.state.isFinished) {
                    onSuccess?.run()
                    refreshAvailableGroupings()
                }
            }
        workObservers.add(ImmutablePair(request.id, workInfoObserver))
        workManager.getWorkInfoByIdLiveData(request.id).observeForever(workInfoObserver)
    }

    fun setGroupCoverContent(groupId: Long, coverContent: Content) {
        val localGroup = dao.selectGroup(groupId) ?: return
        localGroup.coverContent.setAndPutTarget(coverContent)
    }

    fun saveContentPositions(
        orderedContent: List<Content>,
        onSuccess: Runnable
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doSaveContentPositions(orderedContent)
                    GroupHelper.updateGroupsJson(getApplication(), dao)
                }
                onSuccess.run()
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun doSaveContentPositions(orderedContent: List<Content>) {
        val localGroup = group.value ?: return

        // Update the "has custom book order" group flag
        localGroup.hasCustomBookOrder = true
        var order = 0
        for (c in orderedContent) for (gi in localGroup.items) if (gi.content.targetId == c.id) {
            gi.order = order++
            dao.insertGroupItem(gi)
            break
        }
        dao.insertGroup(localGroup)
    }

    fun saveGroupPositions(orderedGroups: List<Group>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doSaveGroupPositions(orderedGroups)
                    GroupHelper.updateGroupsJson(getApplication(), dao)
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun doSaveGroupPositions(orderedGroups: List<Group>) {
        var order = 0
        for (g in orderedGroups) {
            g.order = order++
            dao.insertGroup(g)
        }
    }

    fun getGroupContents(group: Group): List<Content> {
        return dao.selectContent(Helper.getPrimitiveArrayFromList(group.contentIds))
    }

    fun newGroup(
        grouping: Grouping,
        newGroupName: String,
        searchUri: String?,
        onNameExists: Runnable
    ) {
        // Check if the group already exists
        val groupingGroups = dao.selectGroups(grouping.id)
        val groupMatchingName = groupingGroups.filter { g ->
            g.name.equals(newGroupName, ignoreCase = true)
        }
        if (groupMatchingName.isNotEmpty()) { // Existing group with the same name
            onNameExists.run()
        } else {
            val newGroup = Group(grouping, newGroupName, -1)
            if (!searchUri.isNullOrEmpty()) newGroup.searchUri = searchUri
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        dao.insertGroup(newGroup)
                        refreshAvailableGroupings()
                        GroupHelper.updateGroupsJson(getApplication(), dao)
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    fun renameGroup(
        group: Group, newGroupName: String,
        onFail: Consumer<Int>,
        onSuccess: Runnable
    ) {
        // Check if the group already exists
        val localGroups = groups.value ?: return
        val groupMatchingName =
            localGroups.filter { g -> g.name.equals(newGroupName, ignoreCase = true) }
        if (groupMatchingName.isNotEmpty()) { // Existing group with the same name
            onFail.invoke(R.string.group_name_exists)
        } else if (group.grouping == Grouping.CUSTOM && 1 == group.getSubtype()) { // "Ungrouped" group can't be renamed because it stops to work (TODO investgate that)
            onFail.invoke(R.string.group_rename_forbidden)
        } else {
            group.name = newGroupName
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        dao.insertGroup(group)
                        refreshAvailableGroupings()

                        // Update all JSONs of the books inside the renamed group so that they refer to the correct name
                        val builder = UpdateJsonData.Builder()
                        builder.setContentIds(Helper.getPrimitiveArrayFromList(group.contentIds))
                        builder.setUpdateGroups(true)
                        val workManager =
                            WorkManager.getInstance(getApplication())
                        workManager.enqueueUniqueWork(
                            R.id.udpate_json_service.toString(),
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequest.Builder(UpdateJsonWorker::class.java)
                                .setInputData(builder.data)
                                .build()
                        )
                    }
                    onSuccess.run()
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    /**
     * Toggle the "favourite" state of the given group
     *
     * @param group Group whose favourite state to toggle
     */
    fun toggleGroupFavourite(group: Group) {
        if (group.isBeingProcessed) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    doToggleGroupFavourite(group.id)
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    /**
     * Toggle the "favourite" state of the given group
     *
     * @param groupId ID of the group whose favourite state to toggle
     * @return Resulting group
     */
    private fun doToggleGroupFavourite(groupId: Long): Group {
        Helper.assertNonUiThread()

        // Check if given group still exists in DB
        val theGroup = dao.selectGroup(groupId)
            ?: throw InvalidParameterException("Invalid GroupId : $groupId")

        theGroup.isFavourite = !theGroup.isFavourite

        // Persist in it DB
        dao.insertGroup(theGroup)

        // Persist in it JSON
        GroupHelper.updateGroupsJson(getApplication(), dao)
        return theGroup
    }

    /**
     * Set the rating to the given value for the given group IDs
     *
     * @param groupIds     Group IDs to set the rating for
     * @param targetRating Rating to set
     */
    fun rateGroups(groupIds: List<Long>, targetRating: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                groupIds.forEach {
                    try {
                        doRateGroup(it, targetRating)
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            }
        }
    }

    /**
     * Set the rating to the given value for the given group ID
     *
     * @param groupId      Group ID to set the rating for
     * @param targetRating Rating to set
     */
    private fun doRateGroup(groupId: Long, targetRating: Int): Group {
        Helper.assertNonUiThread()

        // Check if given content still exists in DB
        val theGroup = dao.selectGroup(groupId)
            ?: throw InvalidParameterException("Invalid GroupId : $groupId")

        if (!theGroup.isBeingProcessed) {
            theGroup.rating = targetRating
            // Persist in it JSON
            GroupHelper.updateGroupsJson(getApplication(), dao)

            // Persist in it DB
            dao.insertGroup(theGroup)
        }
        return theGroup
    }

    fun moveContentsToNewCustomGroup(
        contentIds: LongArray, newGroupName: String,
        onSuccess: Runnable
    ) {
        val newGroup = Group(Grouping.CUSTOM, newGroupName.trim { it <= ' ' }, -1)
        newGroup.id = dao.insertGroup(newGroup)
        moveContentsToCustomGroup(contentIds, newGroup, onSuccess)
    }

    fun moveContentsToCustomGroup(
        contentIds: LongArray,
        group: Group?,
        onSuccess: Runnable
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentIds.forEach {
                        dao.selectContent(it)?.let { c ->
                            GroupHelper.moveContentToCustomGroup(c, group, dao)
                            ContentHelper.updateJson(getApplication(), c)
                        }
                    }
                    refreshAvailableGroupings()
                    GroupHelper.updateGroupsJson(getApplication(), dao)
                }
                onSuccess.run()
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    fun shuffleContent() {
        RandomSeed.renewSeed(SEED_CONTENT)
        dao.shuffleContent()
    }

    fun mergeContents(
        contentList: List<Content>,
        newTitle: String,
        deleteAfterMerging: Boolean,
        onSuccess: Runnable
    ) {
        if (contentList.isEmpty()) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Flag the content as "being deleted" (triggers blink animation)
                    if (deleteAfterMerging)
                        contentList.forEach { dao.updateContentProcessedFlag(it.id, true) }
                    ContentHelper.mergeContents(getApplication(), contentList, newTitle, dao)
                }
                if (deleteAfterMerging) deleteItems(contentList, emptyList(), false, null)
                onSuccess.run()
            } catch (t: Throwable) {
                Timber.e(t)
                if (deleteAfterMerging)
                    contentList.forEach { dao.updateContentProcessedFlag(it.id, false) }
            }
        }
    }

    fun splitContent(
        content: Content,
        chapters: List<Chapter>,
        onSuccess: Runnable
    ) {
        var result = false
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    doSplitContent(content, chapters)
                    result = true
                } catch (t: ContentNotProcessedException) {
                    Timber.e(t)
                }
            }
            if (result) onSuccess.run()
        }
    }

    @Throws(Exception::class)
    private fun doSplitContent(content: Content, chapters: List<Chapter>) {
        Helper.assertNonUiThread()
        val images: List<ImageFile>? = content.imageFiles
        if (chapters.isEmpty()) throw ContentNotProcessedException(content, "No chapters detected")
        if (images.isNullOrEmpty()) throw ContentNotProcessedException(
            content,
            "No images detected"
        )
        var nbProcessedPics = 0
        val nbImages =
            chapters.flatMap { c -> c.imageList }.count { imf -> imf.isReadable }
        for (chap in chapters) {
            val splitContent = createContentFromChapter(content, chap)

            // Create a new folder for the split content
            val location = ContentHelper.getLocation(content)
            val targetFolder = ContentHelper.getOrCreateContentDownloadDir(
                getApplication(),
                splitContent,
                location,
                true
            )
            if (null == targetFolder || !targetFolder.exists()) throw ContentNotProcessedException(
                splitContent,
                "Could not create target directory"
            )
            splitContent.storageUri = targetFolder.uri.toString()

            // Copy the corresponding images to that folder
            val splitContentImages = splitContent.imageFiles
                ?: throw ContentNotProcessedException(
                    splitContent,
                    "No images detected in generated book"
                )
            for (img in splitContentImages) {
                if (img.status == StatusContent.DOWNLOADED) {
                    val extension = HttpHelper.getExtensionFromUri(img.fileUri)
                    val newUri = FileHelper.copyFile(
                        getApplication(),
                        Uri.parse(img.fileUri),
                        targetFolder.uri,
                        img.mimeType,
                        img.name + "." + extension
                    )
                    if (newUri != null) img.fileUri =
                        newUri.toString() else Timber.w("Could not move file %s", img.fileUri)
                    EventBus.getDefault().post(
                        ProcessEvent(
                            ProcessEvent.Type.PROGRESS,
                            R.id.generic_progress,
                            0,
                            nbProcessedPics++,
                            0,
                            nbImages
                        )
                    )
                }
            }

            // Save the JSON for the new book
            val jsonFile = ContentHelper.createJson(getApplication(), splitContent)
            if (jsonFile != null) splitContent.jsonUri = jsonFile.uri.toString()

            // Save new content (incl. onn-custom group operations)
            ContentHelper.addContent(getApplication(), dao, splitContent)

            // Set custom group, if any
            val customGroups = content.getGroupItems(Grouping.CUSTOM)
            if (customGroups.isNotEmpty()) GroupHelper.moveContentToCustomGroup(
                splitContent, customGroups[0].getGroup(),
                dao
            )
        }
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.generic_progress,
                0,
                nbImages,
                0,
                nbImages
            )
        )
    }

    private fun createContentFromChapter(content: Content, chapter: Chapter): Content {
        val splitContent = Content()
        var url = StringHelper.protect(chapter.url)
        if (url.isEmpty()) { // Default (e.g. manually created chapters)
            url = content.url
            splitContent.site = content.site
        } else { // Detect site and cleanup full URL (e.g. previously merged books)
            val site = Site.searchByUrl(url)
            if (site != null && site != Site.NONE) {
                splitContent.site = site
                url = Content.transformRawUrl(site, url)
            }
        }
        splitContent.url = url
        splitContent.populateUniqueSiteId()
        var id = chapter.uniqueId
        if (id.isEmpty()) id = content.uniqueSiteId + "_" // Don't create a copy of content
        splitContent.uniqueSiteId = id
        splitContent.downloadMode = content.downloadMode
        var newTitle = content.title
        if (!newTitle.contains(chapter.name)) newTitle += " - " + chapter.name // Avoid swelling the title after multiple merges and splits
        splitContent.title = newTitle
        splitContent.uploadDate = content.uploadDate
        splitContent.downloadDate = Instant.now().toEpochMilli()
        splitContent.status = content.status
        splitContent.bookPreferences = content.bookPreferences
        var images: List<ImageFile>? = chapter.imageFiles
        if (images != null) {
            images = chapter.imageList.sortedBy { imf -> imf.order }
            val nbMaxDigits = floor(log10(images.size.toDouble()) + 1).toInt()
            for ((position, img) in images.withIndex()) {
                img.id = 0 // Force working on a new picture
                img.setChapter(null)
                img.content.target = null // Clear content
                img.setIsCover(0 == position)
                img.order = position
                img.computeName(nbMaxDigits)
            }
            splitContent.setImageFiles(images)
            splitContent.setChapters(null)
            splitContent.qtyPages = images.count { imf -> imf.isReadable }
            splitContent.computeSize()
            var coverImageUrl = StringHelper.protect(images[0].url)
            if (coverImageUrl.isEmpty()) coverImageUrl = content.coverImageUrl
            splitContent.coverImageUrl = coverImageUrl
        }
        val splitAttributes = listOf(content).flatMap { c -> c.attributes }
        splitContent.addAttributes(splitAttributes)
        return splitContent
    }

    fun clearSearchHistory() {
        dao.deleteAllSearchRecords()
    }
}