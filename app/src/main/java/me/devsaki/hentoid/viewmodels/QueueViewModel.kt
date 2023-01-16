package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.annimon.stream.Optional
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.download.ContentQueueManager
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.PurgeWorker
import me.devsaki.hentoid.workers.data.DeleteData
import org.apache.commons.lang3.tuple.ImmutablePair
import org.greenrobot.eventbus.EventBus
import org.threeten.bp.Instant
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger


class QueueViewModel(
    application: Application,
    private val dao: CollectionDAO
) : AndroidViewModel(application) {

    // Cleanup for all RxJava calls
    private val compositeDisposable = CompositeDisposable()

    // Collection data for queue
    private var currentQueueSource: LiveData<List<QueueRecord>>? = null
    private val queue = MediatorLiveData<List<QueueRecord>>()

    // Collection data for errors
    private var currentErrorsSource: LiveData<List<Content>>? = null
    private val errors = MediatorLiveData<List<Content>>()

    // ID of the content to show at 1st display
    private val contentHashToShowFirst = MutableLiveData<Long>()

    // Updated whenever a new search is performed
    private val newSearch = MediatorLiveData<Boolean>()


    init {
        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
        compositeDisposable.clear()
    }

    fun getQueue(): LiveData<List<QueueRecord>> {
        return queue
    }

    fun getErrors(): LiveData<List<Content>> {
        return errors
    }

    fun getNewSearch(): LiveData<Boolean> {
        return newSearch
    }

    fun getContentHashToShowFirst(): LiveData<Long> {
        return contentHashToShowFirst
    }

    // =========================
    // =========== QUEUE ACTIONS
    // =========================
    /**
     * Perform a new search
     */
    fun refresh() {
        searchQueueUniversal()
        searchErrorContentUniversal()
    }

    fun searchQueueUniversal(query: String? = null) {
        if (currentQueueSource != null) queue.removeSource(currentQueueSource!!)
        currentQueueSource =
            if (query.isNullOrEmpty()) dao.selectQueueLive()
            else dao.selectQueueLive(query)
        queue.addSource(currentQueueSource!!) { value -> queue.setValue(value) }
        newSearch.value = true
    }

    fun searchErrorContentUniversal(query: String? = null) {
        if (currentErrorsSource != null) errors.removeSource(currentErrorsSource!!)
        currentErrorsSource =
            if (query.isNullOrEmpty()) dao.selectErrorContentLive()
            else dao.selectErrorContentLive(query)
        errors.addSource(currentErrorsSource!!) { value -> errors.setValue(value) }
        newSearch.value = true
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================
    fun moveAbsolute(oldPosition: Int, newPosition: Int) {
        if (oldPosition == newPosition) return
        Timber.d(">> move %s to %s", oldPosition, newPosition)

        // Get unpaged data to be sure we have everything in one collection
        val localQueue = dao.selectQueue()
        if (oldPosition < 0 || oldPosition >= localQueue.size) return

        // Move the item
        val fromValue = localQueue[oldPosition]
        val delta = if (oldPosition < newPosition) 1 else -1
        var i = oldPosition
        while (i != newPosition) {
            localQueue[i] = localQueue[i + delta]
            i += delta
        }
        localQueue[newPosition] = fromValue

        // Renumber everything
        var index = 1
        for (qr in localQueue) qr.rank = index++

        // Update queue in DB
        dao.updateQueue(localQueue)

        // If the 1st item is involved, signal it being skipped
        if (0 == newPosition || 0 == oldPosition) EventBus.getDefault()
            .post(DownloadEvent(DownloadEvent.Type.EV_SKIP))
    }

    /**
     * Move all items at given positions to top of the list
     *
     * @param relativePositions Adapter positions of the items to move
     */
    fun moveTop(relativePositions: List<Int>) {
        val absolutePositions = relativeToAbsolutePositions(relativePositions)
        for ((processed, oldPos) in absolutePositions.withIndex()) {
            moveAbsolute(oldPos, processed)
        }
    }

    /**
     * Move all items at given positions to bottom of the list
     *
     * @param relativePositions Adapter positions of the items to move
     */
    fun moveBottom(relativePositions: List<Int>) {
        val absolutePositions = relativeToAbsolutePositions(relativePositions)
        val dbQueue = dao.selectQueue() ?: return
        val endPos = dbQueue.size - 1
        for ((processed, oldPos) in absolutePositions.withIndex()) {
            moveAbsolute(oldPos - processed, endPos)
        }
    }

    private fun relativeToAbsolutePositions(relativePositions: List<Int>): List<Int> {
        val result: MutableList<Int> = ArrayList()
        val currentQueue = queue.value
        val dbQueue = dao.selectQueue()
        if (null == currentQueue || null == dbQueue) return relativePositions
        for (position in relativePositions) {
            for (i in dbQueue.indices) if (dbQueue[i].id == currentQueue[position].id) {
                result.add(i)
                break
            }
        }
        return result
    }

    fun unpauseQueue() {
        dao.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING)
        ContentQueueManager.getInstance().unpauseQueue()
        ContentQueueManager.getInstance().resumeQueue(getApplication())
    }

    fun invertQueue() {
        // Get unpaged data to be sure we have everything in one collection
        val localQueue = dao.selectQueue()
        if (localQueue.size < 2) return

        // Renumber everything in reverse order
        var index = 1
        for (i in localQueue.indices.reversed()) {
            localQueue[i].rank = index++
        }

        // Update queue and signal skipping the 1st item
        dao.updateQueue(localQueue)
        EventBus.getDefault().post(DownloadEvent(DownloadEvent.Type.EV_SKIP))
    }

    /**
     * Cancel download of designated Content
     * NB : Contrary to Pause command, Cancel removes the Content from the download queue
     *
     * @param contents Contents whose download has to be canceled
     */
    fun cancel(contents: List<Content>) {
        remove(contents)
    }

    fun removeAll() {
        val errorsLocal = dao.selectErrorContent()
        if (errorsLocal.isEmpty()) return
        remove(errorsLocal)
    }

    fun remove(contentList: List<Content>) {
        val builder = DeleteData.Builder()
        if (contentList.isNotEmpty()) builder.setQueueIds(
            contentList.map { c -> c.id }.filter { id -> id > 0 }
        )
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.delete_service_delete.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(DeleteWorker::class.java).setInputData(builder.data).build()
        )
    }

    private fun purgeItem(content: Content) {
        val builder = DeleteData.Builder()
        builder.setContentPurgeIds(listOf(content.id))
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.delete_service_purge.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(PurgeWorker::class.java).setInputData(builder.data).build()
        )
    }

    fun cancelAll() {
        val localQueue = dao.selectQueue()
        if (localQueue.isEmpty()) return
        val contentIdList = localQueue.map { qr -> qr.content.targetId }.filter { id -> id > 0 }
        EventBus.getDefault().post(DownloadEvent(DownloadEvent.Type.EV_PAUSE))
        val builder = DeleteData.Builder()
        if (contentIdList.isNotEmpty()) builder.setQueueIds(contentIdList)
        builder.setDeleteAllQueueRecords(true)
        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueueUniqueWork(
            R.id.delete_service_delete.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(DeleteWorker::class.java).setInputData(builder.data).build()
        )
    }

    /**
     * Redownload the given list of Content according to the given parameters
     * NB : Used by both the regular redownload and redownload from scratch
     *
     * @param contentList    List of content to be redownloaded
     * @param reparseContent True if the content (general metadata) has to be re-parsed from the site; false to keep
     * @param reparseImages  True if the images have to be re-detected and redownloaded from the site; false to keep
     * @param position       Position of the new item to redownload, either QUEUE_NEW_DOWNLOADS_POSITION_TOP or QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM
     * @param onSuccess      Handler for process success; consumes the number of books successfuly redownloaded
     * @param onError        Handler for process error; consumes the exception
     */
    fun redownloadContent(
        contentList: List<Content>,
        reparseContent: Boolean,
        reparseImages: Boolean,
        position: Int,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val targetImageStatus = if (reparseImages) StatusContent.ERROR else null
        val errorCount = AtomicInteger(0)
        val okCount = AtomicInteger(0)
        compositeDisposable.add(
            Observable.fromIterable(contentList)
                .observeOn(Schedulers.io())
                .map { c: Content ->
                    if (reparseContent) ContentHelper.reparseFromScratch(
                        c
                    ) else ImmutablePair(
                        c,
                        Optional.of(c)
                    )
                }
                .doOnNext { res: ImmutablePair<Content, Optional<Content>> ->
                    if (res.right.isPresent) {
                        val content = res.right.get()
                        // Non-blocking performance bottleneck; run in a dedicated worker
                        if (reparseImages) purgeItem(content)
                        okCount.incrementAndGet()
                        dao.addContentToQueue(
                            content, targetImageStatus, position, -1,
                            ContentQueueManager.getInstance()
                                .isQueueActive(getApplication())
                        )
                    } else {
                        // As we're in the download queue, an item whose content is unreachable should directly get to the error queue
                        val c =
                            dao.selectContent(res.left.id)
                        if (c != null) {
                            // Remove the content from the regular queue
                            ContentHelper.removeQueuedContent(
                                getApplication(),
                                dao,
                                c,
                                false
                            )
                            // Put it in the error queue
                            c.status = StatusContent.ERROR
                            val errors: MutableList<ErrorRecord> =
                                ArrayList()
                            errors.add(
                                ErrorRecord(
                                    c.id,
                                    ErrorType.PARSING,
                                    c.galleryUrl,
                                    "Book",
                                    "Redownload from scratch -> Content unreachable",
                                    Instant.now()
                                )
                            )
                            c.setErrorLog(errors)
                            dao.insertContent(c)
                            // Save the regular queue
                            ContentHelper.updateQueueJson(getApplication(), dao)
                        }
                        errorCount.incrementAndGet()
                        onError.invoke(EmptyResultException("Redownload from scratch -> Content unreachable"))
                    }
                    EventBus.getDefault().post(
                        ProcessEvent(
                            ProcessEvent.EventType.PROGRESS,
                            R.id.generic_progress,
                            0,
                            okCount.get(),
                            errorCount.get(),
                            contentList.size
                        )
                    )
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    if (Preferences.isQueueAutostart()) ContentQueueManager.getInstance()
                        .resumeQueue(getApplication())
                    EventBus.getDefault().post(
                        ProcessEvent(
                            ProcessEvent.EventType.COMPLETE,
                            R.id.generic_progress,
                            0,
                            okCount.get(),
                            errorCount.get(),
                            contentList.size
                        )
                    )
                    onSuccess.invoke(contentList.size - errorCount.get())
                }
                .subscribe(
                    {
                        EventBus.getDefault().post(
                            ProcessEvent(
                                ProcessEvent.EventType.COMPLETE,
                                R.id.generic_progress,
                                0,
                                contentList.size - errorCount.get(),
                                errorCount.get(),
                                contentList.size
                            )
                        )
                    }
                ) { t: Throwable -> onError.invoke(t) }
        )
    }

    fun setContentToShowFirst(hash: Long) {
        contentHashToShowFirst.value = hash
    }

    fun setDownloadMode(contentIds: List<Long>, downloadMode: Int) {
        compositeDisposable.add(
            Observable.fromIterable(contentIds)
                .observeOn(Schedulers.io())
                .map { id: Long ->
                    doSetDownloadMode(id, downloadMode)
                }
                .doOnComplete { -> // Update queue JSON
                    ContentHelper.updateQueueJson(getApplication(), dao)
                    // Force display by updating queue
                    dao.updateQueue(dao.selectQueue())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { }
                ) { t: Throwable? -> Timber.w(t) }
        )
    }

    private fun doSetDownloadMode(contentId: Long, downloadMode: Int): Content? {
        Helper.assertNonUiThread()

        // Check if given content still exists in DB
        val theContent = dao.selectContent(contentId)
        if (theContent != null && !theContent.isBeingDeleted) {
            theContent.downloadMode = downloadMode
            // Persist in it DB
            dao.insertContent(theContent)
        }
        return theContent
    }

    fun toogleFreeze(recordId: List<Long>) {
        runBlocking {
            launch(Dispatchers.IO) {
                Helper.assertNonUiThread() // TODO

                val queue = dao.selectQueue()
                queue.forEach {
                    if (recordId.contains(it.id)) it.isFrozen = !it.isFrozen
                }
                dao.updateQueue(queue)
                // Update queue JSON
                ContentHelper.updateQueueJson(getApplication(), dao)
            }
        }
    }
}