package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.IdRes
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.ObjectBoxDB
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.safeFindIds
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.delete.DeleteCompleteNotification
import me.devsaki.hentoid.notification.delete.DeleteProgressNotification
import me.devsaki.hentoid.notification.delete.DeleteStartNotification
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.exception.FileNotProcessedException
import me.devsaki.hentoid.util.fetchImageURLs
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.getStackTraceString
import me.devsaki.hentoid.util.isDownloadable
import me.devsaki.hentoid.util.moveContentToCustomGroup
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.purgeFiles
import me.devsaki.hentoid.util.removeContent
import me.devsaki.hentoid.util.removeQueuedContent
import me.devsaki.hentoid.util.reparseFromScratch
import me.devsaki.hentoid.util.updateGroupsJson
import me.devsaki.hentoid.util.updateJson
import me.devsaki.hentoid.util.updateQueueJson
import me.devsaki.hentoid.widget.ContentSearchManager
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import me.devsaki.hentoid.workers.data.DeleteData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import kotlin.math.ceil

/**
 * Worker responsible for deleting content in the background
 */
abstract class BaseDeleteWorker(
    context: Context,
    @IdRes serviceId: Int,
    parameters: WorkerParameters
) :
    BaseWorker(context, parameters, serviceId, "delete") {

    enum class Operation {
        DELETE, PURGE, STREAM
    }

    // Operation to perform
    private val operation: Operation

    // True to keep cover pictures when purging
    private val contentPurgeKeepCovers: Boolean

    // Content IDs to delete
    private val contentIds: LongArray

    // Groups IDs to delete
    private val groupIds: LongArray

    // QueueRecord IDs to delete
    private val queueIds: LongArray

    // ImageFile IDs to delete
    private val imageIds: LongArray

    // Uris of the DocumentFiles to delete
    private val docUris: List<Uri>

    // True to delete all queue records
    private val isDeleteAllQueueRecords: Boolean

    // True to delete groups only, without their books
    private val isDeleteGroupsOnly: Boolean

    // == VARIABLES

    private var deleteMax = 0
    private var deleteProgress = 0
    private var nbError = 0

    private val dao: CollectionDAO = ObjectBoxDAO()


    init {
        val inputData = DeleteData.Parser(inputData)
        var askedContentIds = inputData.contentIds
        contentPurgeKeepCovers = inputData.contentPurgeKeepCovers
        groupIds = inputData.groupIds
        queueIds = inputData.queueIds
        docUris = inputData.docUris
        val isDeleteFlaggedImages = inputData.isDeleteFlaggedImages
        isDeleteAllQueueRecords = inputData.isDeleteAllQueueRecords
        isDeleteGroupsOnly = inputData.isDeleteGroupsOnly
        operation = inputData.operation ?: throw IllegalArgumentException("Must set an Operation")

        // Use a query to avoid serialization hard-limit of androidx.work.Data.Builder
        // when passing a large long[] through DeleteData
        // NB : Using a filter overrides any value set in contentIds
        if (!inputData.contentFilter.isEmpty) {
            val csb = ContentSearchBundle(inputData.contentFilter)

            val currentFilterContent =
                ContentSearchManager.searchContentIds(csb, dao).toSet()

            val scope = if (inputData.isInvertFilterScope) {
                val processedContentIds: MutableSet<Long> = HashSet()
                dao.streamStoredContent(false, -1, false)
                { c -> if (!currentFilterContent.contains(c.id)) processedContentIds.add(c.id) }
                processedContentIds
            } else {
                currentFilterContent
            }

            askedContentIds = if (inputData.isKeepFavGroups) {
                val favGroupsContent = dao.selectStoredFavContentIds(
                    bookFavs = false,
                    groupFavs = true
                ).toSet()
                scope.filterNot { favGroupsContent.contains(it) }.toLongArray()
            } else {
                scope.toLongArray()
            }
        }
        contentIds = askedContentIds

        // Use pre-flagging to avoid serialization hard-limit of androidx.work.Data.Builder
        // when passing a large long[] through DeleteData
        imageIds = if (isDeleteFlaggedImages) {
            ObjectBoxDB.selectAllFlaggedImgsQ().safeFindIds()
        } else longArrayOf()

        deleteMax = contentIds.size + groupIds.size + queueIds.size + imageIds.size + docUris.size
    }

    override fun getStartNotification(): BaseNotification {
        return DeleteStartNotification(deleteMax, operation)
    }

    override fun onInterrupt() {
        // Nothing to do here
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        // Nothing
    }

    override fun runProgressNotification() {
        // Using custom implementation
    }

    override suspend fun getToWork(input: Data) {
        deleteProgress = 0
        nbError = 0

        withContext(Dispatchers.IO) {
            try {
                // First chain contents, then groups (to be sure to delete empty groups only)
                if (contentIds.isNotEmpty()) processContentList(
                    contentIds,
                    operation,
                    contentPurgeKeepCovers
                )
                if (groupIds.isNotEmpty()) removeGroups(groupIds, isDeleteGroupsOnly)

                // Remove Contents and associated QueueRecords
                if (queueIds.isNotEmpty()) removeQueue(queueIds)

                // Remove files linked to the given ImageFile IDs
                if (imageIds.isNotEmpty()) removeImageFiles(imageIds)

                // Remove documentFiles linked to the given Uris
                if (docUris.isNotEmpty()) removeDocuments(docUris)

                // If asked, make sure all QueueRecords are removed including dead ones
                if (isDeleteAllQueueRecords) dao.deleteQueueRecordsCore()
            } finally {
                dao.cleanup()
            }
        }
        progressDone()
    }

    private suspend fun processContentList(
        ids: LongArray,
        operation: Operation,
        purgeKeepCovers: Boolean = false
    ) {
        // Process the list 50 by 50 items
        val nbPackets = ceil(ids.size / 50f).toInt()
        for (i in 0 until nbPackets) {
            val minIndex = i * 50
            val maxIndex = ((i + 1) * 50).coerceAtMost(ids.size)
            // Flag the content as "being processed" (triggers blink animation; lock operations)
            val toProcess = ids.slice(minIndex..<maxIndex).filter { it > 0 }.toList()
            dao.updateContentsProcessedFlagById(toProcess, true)
            // Process it
            for (id in minIndex until maxIndex) {
                dao.selectContent(ids[id])?.let {
                    try {
                        when (operation) {
                            Operation.DELETE -> deleteContent(it)
                            Operation.PURGE -> purgeContentFiles(it, purgeKeepCovers)
                            Operation.STREAM -> streamContent(it)
                        }
                    } catch (e: Exception) {
                        nbError++
                        trace(Log.WARN, "Error when trying to delete %s : ${e.message}", it.id)
                        Timber.w(e)
                    } finally {
                        dao.updateContentProcessedFlag(it.id, false)
                    }
                }
                if (isStopped) break
            }
        }
    }

    /**
     * Delete the given content
     *
     * @param content Content to be deleted
     */
    private suspend fun deleteContent(content: Content) {
        progressItem(content, DeleteProgressNotification.ProgressType.DELETE_BOOKS)
        try {
            removeContent(applicationContext, dao, content)
            trace(Log.INFO, "Removed item: %s from database and file system.", content.title)
        } catch (_: ContentNotProcessedException) {
            nbError++
            trace(Log.WARN, "Error when trying to delete %s", content.id)
        } catch (e: Exception) {
            nbError++
            trace(
                Log.WARN,
                "Error when trying to delete %s : %s - %s",
                content.title,
                e.message,
                getStackTraceString(e)
            )
        }
    }

    private fun streamContent(content: Content) {
        Timber.d("Checking pages availability")
        // Reparse content from scratch if images KO
        val res = if (!isDownloadable(content)) {
            trace(Log.INFO, "Pages unreachable; reparsing content %s", content.title)
            // Reparse content itself
            val newContent = reparseFromScratch(content)
            if (null == newContent) null
            else {
                // Reparse pages
                val newImages =
                    fetchImageURLs(
                        newContent,
                        newContent.galleryUrl,
                        StatusContent.ONLINE
                    )
                newContent.setImageFiles(newImages)
                // Associate new pages' cover with current cover file (that won't be deleted)
                newContent.cover.status = StatusContent.DOWNLOADED
                newContent.cover.fileUri = content.cover.fileUri
                // Save everything
                dao.replaceImageList(newContent.id, newImages)
                newContent
            }
        } else content

        if (res != null) {
            // Use an updated content from the DB
            val updatedContent = dao.selectContent(res.id)?.let {
                purgeFiles(applicationContext, it, removeJson = false, removeCover = false)
                it
            }
            // Use an updated content from the DB again
            dao.selectContent(res.id)?.let {
                // Apply changes from the purge
                updatedContent?.let { up ->
                    up.getStorageDoc()?.let { doc ->
                        it.setStorageDoc(doc)
                    }
                    it.jsonUri = up.jsonUri
                }
                // Update content folder and JSON Uri's after purging
                it.downloadMode = DownloadMode.STREAM
                dao.insertContentCore(it)
                val imgs: List<ImageFile> = it.imageList
                for (img in imgs) {
                    img.fileUri = ""
                    img.size = 0
                    img.status = StatusContent.ONLINE
                }
                dao.insertImageFiles(imgs)
                it.size = 0
                it.isBeingProcessed = false
                dao.insertContent(it)
                updateJson(applicationContext, it)
            }
            trace(Log.INFO, "Streaming succeeded for %s", content.title)
        } else {
            trace(Log.WARN, "Streaming failed for %s", content.title)
        }
    }

    /**
     * Purge files from the given content
     *
     * @param content Content to be purged
     */
    private fun purgeContentFiles(content: Content, removeCover: Boolean) {
        progressItem(content, DeleteProgressNotification.ProgressType.PURGE_BOOKS)
        try {
            purgeFiles(applicationContext, content, false, removeCover)

            // Use an updated content from the DB
            dao.selectContent(content.id)?.let { freshC ->
                // Apply changes from the purge
                content.getStorageDoc()?.let { doc ->
                    freshC.setStorageDoc(doc)
                }
                freshC.jsonUri = content.jsonUri

                // Update content folder and JSON Uri's after purging
                dao.insertContentCore(freshC)
            }
            trace(Log.INFO, "Purged item: %s.", content.title)
        } catch (e: Exception) {
            nbError++
            Timber.w(e)
            trace(Log.WARN, "Error when trying to purge %s : %s", content.title, e.message)
        }
    }

    private suspend fun removeGroups(ids: LongArray, deleteGroupsOnly: Boolean) {
        val groups = dao.selectGroups(ids)
        try {
            for (g in groups) {
                deleteGroup(g, deleteGroupsOnly)
                if (isStopped) break
            }
        } finally {
            updateGroupsJson(applicationContext, dao)
        }
    }

    /**
     * Delete the given group
     * WARNING : If the group contains GroupItems, it will be ignored
     * This method is aimed to be used to delete empty groups when using Custom grouping
     *
     * @param group Group to be deleted
     */
    private suspend fun deleteGroup(group: Group, deleteGroupsOnly: Boolean) {
        var theGroup: Group? = group
        progressItem(theGroup, DeleteProgressNotification.ProgressType.DELETE_BOOKS)
        try {
            // Reassign group for contained items
            if (deleteGroupsOnly) {
                val containedContentList = dao.selectContent(theGroup!!.contentIds.toLongArray())
                for (c in containedContentList) {
                    val movedContent = moveContentToCustomGroup(c, null, dao)
                    updateJson(applicationContext, movedContent)
                }
                theGroup = dao.selectGroup(theGroup.id)
            } else if (theGroup!!.grouping == Grouping.DYNAMIC) { // Delete books from dynamic group
                val bundle = ContentSearchBundle()
                bundle.groupId = theGroup.id
                val containedContentList = dao.searchBookIdsUniversal(bundle).toLongArray()
                processContentList(containedContentList, Operation.DELETE)
            }
            if (theGroup != null) {
                if (theGroup.getItems().isNotEmpty()) {
                    nbError++
                    trace(Log.WARN, "Group is not empty : %s", theGroup.name)
                    return
                }
                dao.deleteGroup(theGroup.id)
                trace(Log.INFO, "Removed group: %s from database.", theGroup.name)
            }
        } catch (e: Exception) {
            nbError++
            trace(Log.WARN, "Error when trying to delete group %d : %s", group.id, e.message)
        }
    }

    private suspend fun removeQueue(ids: LongArray) {
        val contents = dao.selectContent(ids)
        try {
            for (c in contents) {
                removeQueuedContent(c)
                if (isStopped) break
            }
        } finally {
            if (updateQueueJson(applicationContext, dao)) trace(
                Log.INFO,
                "Queue JSON successfully saved"
            ) else trace(
                Log.WARN, "Queue JSON saving failed"
            )
        }
    }

    private suspend fun removeQueuedContent(content: Content) {
        try {
            progressItem(content, DeleteProgressNotification.ProgressType.DELETE_BOOKS)
            removeQueuedContent(applicationContext, dao, content, true)
        } catch (e: ContentNotProcessedException) {
            // Don't throw the exception if we can't remove something that isn't there
            if (!(e is FileNotProcessedException && content.storageUri.isEmpty())) {
                nbError++
                trace(
                    Log.WARN,
                    "Error when trying to delete queued %s : %s",
                    content.title,
                    e.message
                )
            }
        }
    }

    private fun removeImageFiles(ids: LongArray) {
        val imgs = dao.selectImageFiles(ids)
        trace(Log.INFO, "Removing %s images...", imgs.size)
        val uris = imgs.map { it.fileUri }
        val contentIds = imgs.map { it.contentId }.distinct()
        dao.deleteImageFiles(imgs)
        uris.forEachIndexed { index, uri ->
            if (isStopped) return
            removeFile(applicationContext, uri.toUri())
            progressItem(
                "Image ${index + 1}",
                DeleteProgressNotification.ProgressType.DELETE_IMAGES
            )
        }

        // Update content JSON if it exists (i.e. if book is not queued)
        contentIds.forEach { contentId ->
            dao.selectContent(contentId)?.let { content ->
                if (content.jsonUri.isNotEmpty())
                    updateJson(applicationContext, content)
            }
        }
        progressDone()
        trace(Log.INFO, "Removed %s images", imgs.size)
    }

    private fun removeDocuments(uris: List<Uri>) {
        trace(Log.INFO, "Removing ${uris.size} documents...")

        uris.forEachIndexed { index, uri ->
            if (isStopped) return
            removeFile(applicationContext, uri)
            progressItem(
                "Document " + (index + 1).toString(),
                DeleteProgressNotification.ProgressType.DELETE_DOCS
            )
        }

        progressDone()
        trace(Log.INFO, "Removed ${uris.size} documents")
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun progressItem(item: Any?, type: DeleteProgressNotification.ProgressType) {
        var title: String? = null
        when (item) {
            is Content -> title = item.title
            is Group -> title = item.name
            is String -> title = item
        }
        if (title != null) {
            deleteProgress++
            // Handle notifications on another coroutine not to steal focus for unnecessary stuff
            GlobalScope.launch(Dispatchers.Default) {
                notificationManager.notify(
                    DeleteProgressNotification(
                        title,
                        deleteProgress + nbError,
                        deleteMax,
                        type
                    )
                )
                EventBus.getDefault().post(
                    ProcessEvent(
                        ProcessEvent.Type.PROGRESS,
                        R.id.generic_progress,
                        0,
                        deleteProgress,
                        nbError,
                        deleteMax
                    )
                )
            }
        }
    }

    private fun progressDone() {
        notificationManager.notifyLast(
            DeleteCompleteNotification(
                deleteMax,
                nbError,
                operation
            )
        )
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.generic_progress,
                0,
                deleteProgress,
                nbError,
                deleteMax
            )
        )
    }
}

class DeleteWorker(context: Context, parameters: WorkerParameters) :
    BaseDeleteWorker(context, R.id.delete_service_delete, parameters)

class PurgeWorker(context: Context, parameters: WorkerParameters) :
    BaseDeleteWorker(context, R.id.delete_service_purge, parameters)