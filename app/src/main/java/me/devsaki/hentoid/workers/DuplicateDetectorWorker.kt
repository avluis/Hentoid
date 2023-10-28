package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.duplicates.DuplicateCompleteNotification
import me.devsaki.hentoid.notification.duplicates.DuplicateProgressNotification
import me.devsaki.hentoid.notification.duplicates.DuplicateStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.DuplicateHelper.DuplicateCandidate
import me.devsaki.hentoid.util.DuplicateHelper.indexCovers
import me.devsaki.hentoid.util.DuplicateHelper.processContent
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.string_similarity.Cosine
import me.devsaki.hentoid.util.string_similarity.StringSimilarity
import me.devsaki.hentoid.workers.data.DuplicateData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DuplicateDetectorWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.duplicate_detector_service, "duplicate_detector") {

    companion object {
        // Processing steps
        const val STEP_COVER_INDEX = 0
        const val STEP_DUPLICATES = 1

        fun isRunning(context: Context): Boolean {
            return isRunning(context, R.id.duplicate_detector_service)
        }
    }

    private val dao: CollectionDAO
    private val duplicatesDao: DuplicatesDAO

    private val currentIndex = AtomicInteger(0)
    private val stopped = AtomicBoolean(false)


    init {
        dao = ObjectBoxDAO(applicationContext)
        duplicatesDao = DuplicatesDAO(applicationContext)
    }

    override fun getStartNotification(): BaseNotification {
        return DuplicateStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear() {
        if (!isStopped && !isComplete) Preferences.setDuplicateLastIndex(currentIndex.get())
        else Preferences.setDuplicateLastIndex(-1)
        stopped.set(true)
        dao.cleanup()
        duplicatesDao.cleanup()
    }

    override fun getToWork(input: Data) {
        val inputData = DuplicateData.Parser(input)
        if (inputData.useCover) {
            // Run cover indexing in the background
            trace(Log.INFO, "Covers to index : %s", dao.countContentWithUnhashedCovers())
            indexCovers(
                applicationContext,
                dao, stopped,
                { c: Content ->
                    indexContentInfo(c)
                }, { progress: Int, max: Int -> notifyIndexProgress(progress, max) }
            ) { t: Throwable -> indexError(t) }
            trace(Log.INFO, "Indexing done")
        }

        // No need to continue if the process has already been stopped
        if (isStopped) return

        // Initialize duplicate detection
        detectDuplicates(
            inputData.useTitle,
            inputData.useCover,
            inputData.useArtist,
            inputData.useSameLanguage,
            inputData.ignoreChapters,
            inputData.sensitivity
        )
    }

    private fun detectDuplicates(
        useTitle: Boolean,
        useCover: Boolean,
        useArtist: Boolean,
        useSameLanguage: Boolean,
        ignoreChapters: Boolean,
        sensitivity: Int
    ) {
        // Mark process as incomplete until all combinations are searched
        // to support abort and retry
        isComplete = false
        val matchedIds: MutableMap<Long, MutableList<Long>> = HashMap()
        val reverseMatchedIds: MutableMap<Long, MutableList<Long>> = HashMap()

        // Retrieve number of lines done in previous iteration (ended with RETRY)
        val startIndex = Preferences.getDuplicateLastIndex() + 1
        if (0 == startIndex) duplicatesDao.clearEntries() else {
            trace(Log.DEBUG, "Resuming from index %d", startIndex)
            // Pre-populate matchedIds and reverseMatchedIds using existing duplicates
            val entries = duplicatesDao.getEntries()
            entries.forEach {
                processEntry(it.referenceId, it.duplicateId, matchedIds, reverseMatchedIds)
            }
        }
        trace(Log.DEBUG, "Preparation started")
        // Pre-compute all book entries as DuplicateCandidates
        val candidates: MutableList<DuplicateCandidate> = ArrayList()
        dao.streamStoredContent(
            false, Preferences.Constant.ORDER_FIELD_SIZE, true
        ) { content: Content? ->
            candidates.add(
                DuplicateCandidate(
                    content!!,
                    useTitle,
                    useArtist,
                    useSameLanguage,
                    useCover,
                    ignoreChapters,
                    Long.MIN_VALUE
                )
            )
        }
        trace(Log.DEBUG, "Detection started for %d books", candidates.size)
        processAll(
            duplicatesDao,
            candidates,
            matchedIds,
            reverseMatchedIds,
            startIndex,
            useTitle,
            useCover,
            useArtist,
            useSameLanguage,
            ignoreChapters,
            sensitivity
        )
        trace(
            Log.DEBUG, "Final End reached (currentIndex=%d, complete=%s)", currentIndex.get(),
            isComplete
        )
        isComplete = true
        matchedIds.clear()
    }

    private fun processAll(
        duplicatesDao: DuplicatesDAO,
        library: List<DuplicateCandidate>,
        matchedIds: MutableMap<Long, MutableList<Long>>,
        reverseMatchedIds: MutableMap<Long, MutableList<Long>>,
        startIndex: Int,
        useTitle: Boolean,
        useCover: Boolean,
        useSameArtist: Boolean,
        useSameLanguage: Boolean,
        ignoreChapters: Boolean,
        sensitivity: Int
    ) {
        val tempResults: MutableList<DuplicateEntry> = ArrayList()
        val cosine: StringSimilarity = Cosine()
        val max = library.size - 1
        for (i in startIndex until library.size) {
            if (isStopped) return
            val reference = library[i]
            for (j in i + 1 until library.size) {
                if (isStopped) return
                val candidate = library[j]
                val entry = processContent(
                    reference,
                    candidate,
                    useTitle,
                    useCover,
                    useSameArtist,
                    useSameLanguage,
                    ignoreChapters,
                    sensitivity,
                    cosine
                )
                if (entry != null && processEntry(
                        entry.referenceId,
                        entry.duplicateId,
                        matchedIds,
                        reverseMatchedIds
                    )
                ) tempResults.add(entry)
            }

            // Save results for this reference
            if (tempResults.isNotEmpty()) {
                duplicatesDao.insertEntries(tempResults)
                tempResults.clear()
            }
            currentIndex.set(i)
            if (0 == i % 10) notifyProcessProgress(
                i,
                max
            ) // Only update every 10 iterations for performance
        }
        notifyProcessProgress(max, max)
    }

    private fun indexContentInfo(c: Content) {
        // No need for that unless we're debugging
        if (BuildConfig.DEBUG) trace(
            Log.DEBUG,
            "Indexing %s/%s",
            c.site.name,
            ContentHelper.formatBookFolderName(c).left
        )
    }

    private fun indexError(t: Throwable) {
        Timber.w(t)
        val message = t.message
        if (message != null) trace(Log.WARN, "Indexing error : %s", message)
    }

    private fun processEntry(
        referenceId: Long,
        candidateId: Long,
        matchedIds: MutableMap<Long, MutableList<Long>>,
        reverseMatchedIds: MutableMap<Long, MutableList<Long>>
    ): Boolean {
        // Check if matched IDs don't already contain the reference as a transitive link
        // TODO doc
        var transitiveMatchFound = false
        val reverseMatchesC: List<Long>? = reverseMatchedIds[candidateId]
        if (!reverseMatchesC.isNullOrEmpty()) {
            val reverseMatchesRef: List<Long>? = reverseMatchedIds[referenceId]
            if (!reverseMatchesRef.isNullOrEmpty()) {
                for (lc in reverseMatchesC) {
                    for (lr in reverseMatchesRef) if (lc == lr) {
                        transitiveMatchFound = true
                        break
                    }
                    if (transitiveMatchFound) break
                }
            }
        }
        // Record the entry
        if (!transitiveMatchFound) {
            var matches = matchedIds[referenceId]
            if (null == matches) matches = ArrayList()
            matches.add(candidateId)
            matchedIds[referenceId] = matches
            var reverseMatches = reverseMatchedIds[candidateId]
            if (null == reverseMatches) reverseMatches = ArrayList()
            reverseMatches.add(referenceId)
            reverseMatchedIds[candidateId] = reverseMatches
            return true
        }
        return false
    }

    private fun notifyIndexProgress(progress: Int, max: Int) {
        Timber.i(">> indexing progress %s", progress * 1f / max)
        if (progress < max) {
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.duplicate_index,
                    STEP_COVER_INDEX,
                    progress,
                    0,
                    max
                )
            )
        } else {
            EventBus.getDefault().postSticky(
                ProcessEvent(
                    ProcessEvent.Type.COMPLETE,
                    R.id.duplicate_index,
                    STEP_COVER_INDEX,
                    progress,
                    0,
                    max
                )
            )
        }
    }

    private fun notifyProcessProgress(progress: Int, max: Int) {
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Default) {
                doNotifyProcessProgress(progress, max)
            }
        }
    }

    private fun doNotifyProcessProgress(progress: Int, max: Int) {
        if (progress < max) {
            setForegroundAsync(
                notificationManager.buildForegroundInfo(
                    DuplicateProgressNotification(progress, max)
                )
            )
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.duplicate_detect,
                    STEP_DUPLICATES,
                    progress,
                    0,
                    max
                )
            )
        } else {
            setForegroundAsync(
                notificationManager.buildForegroundInfo(
                    DuplicateCompleteNotification(0)
                )
            )
            EventBus.getDefault().postSticky(
                ProcessEvent(
                    ProcessEvent.Type.COMPLETE,
                    R.id.duplicate_detect,
                    STEP_DUPLICATES,
                    progress,
                    0,
                    max
                )
            )
        }
    }
}