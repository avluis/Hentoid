package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.annotation.IdRes
import androidx.work.Data
import androidx.work.WorkerParameters
import com.annimon.stream.Optional
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.delete.DeleteCompleteNotification
import me.devsaki.hentoid.notification.delete.DeleteProgressNotification
import me.devsaki.hentoid.notification.delete.DeleteStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.GroupHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.exception.FileNotProcessedException
import me.devsaki.hentoid.util.notification.BaseNotification
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

    private val contentIds: LongArray
    private val contentPurgeIds: LongArray
    private val contentPurgeKeepCovers: Boolean
    private val groupIds: LongArray
    private val queueIds: LongArray
    private val isDeleteAllQueueRecords: Boolean
    private val isDeleteGroupsOnly: Boolean
    private val isDownloadPrepurge: Boolean
    private val operation: ToolsActivity.MassOperation

    private var deleteMax = 0
    private var deleteProgress = 0
    private var nbError = 0

    private val dao: CollectionDAO

    init {
        val inputData = DeleteData.Parser(inputData)
        var askedContentIds = inputData.contentIds
        contentPurgeIds = inputData.contentPurgeIds
        contentPurgeKeepCovers = inputData.contentPurgeKeepCovers
        groupIds = inputData.groupIds
        queueIds = inputData.queueIds
        isDeleteAllQueueRecords = inputData.isDeleteAllQueueRecords
        isDeleteGroupsOnly = inputData.isDeleteGroupsOnly
        isDownloadPrepurge = inputData.isDownloadPrepurge
        operation =
            if (1 == inputData.massOperation) ToolsActivity.MassOperation.STREAM else ToolsActivity.MassOperation.DELETE
        dao = ObjectBoxDAO()

        // Queried here to avoid serialization hard-limit of androidx.work.Data.Builder
        // when passing a large long[] through DeleteData
        val csb = ContentSearchBundle(inputData.massFilter)
        if (inputData.massOperation > -1) {
            val currentFilterContent =
                ContentSearchManager.searchContentIds(csb, dao).toSet()

            val scope = if (inputData.isMassInvertScope) {
                val processedContentIds: MutableSet<Long> = HashSet()
                dao.streamStoredContent(false, -1, false)
                { c -> if (!currentFilterContent.contains(c.id)) processedContentIds.add(c.id) }
                processedContentIds
            } else {
                currentFilterContent
            }

            askedContentIds = if (inputData.isMassKeepFavGroups) {
                val favGroupsContent = dao.selectStoredFavContentIds(false, true).toSet()
                scope.filterNot { e -> favGroupsContent.contains(e) }.toLongArray()
            } else {
                scope.toLongArray()
            }
        }
        contentIds = askedContentIds
        deleteMax = contentIds.size + contentPurgeIds.size + groupIds.size + queueIds.size
    }

    override fun getStartNotification(): BaseNotification {
        return DeleteStartNotification(deleteMax, deleteMax == contentPurgeIds.size)
    }

    override fun onInterrupt() {
        // Nothing to do here
    }

    override fun onClear() {
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
        deleteProgress = 0
        nbError = 0

        // First chain contents, then groups (to be sure to delete empty groups only)
        if (contentIds.isNotEmpty()) processContentList(contentIds, operation)
        if (contentPurgeIds.isNotEmpty()) purgeContentList(contentPurgeIds, contentPurgeKeepCovers)
        if (groupIds.isNotEmpty()) removeGroups(groupIds, isDeleteGroupsOnly)

        // Remove Contents and associated QueueRecords
        if (queueIds.isNotEmpty()) removeQueue(queueIds)
        // If asked, make sure all QueueRecords are removed including dead ones
        if (isDeleteAllQueueRecords) dao.deleteQueueRecordsCore()
        progressDone()
    }

    private fun processContentList(ids: LongArray, operation: ToolsActivity.MassOperation) {
        // Process the list 50 by 50 items
        val nbPackets = ceil((ids.size / 50f).toDouble()).toInt()
        for (i in 0 until nbPackets) {
            val minIndex = i * 50
            val maxIndex = ((i + 1) * 50).coerceAtMost(ids.size)
            // Flag the content as "being processed" (triggers blink animation; lock operations)
            for (id in minIndex until maxIndex) {
                if (ids[id] > 0) dao.updateContentProcessedFlag(ids[id], true)
                if (isStopped) break
            }
            // Process it
            for (id in minIndex until maxIndex) {
                dao.selectContent(ids[id])?.let {
                    if (operation == ToolsActivity.MassOperation.DELETE) deleteContent(it)
                    else streamContent(it)
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
    private fun deleteContent(content: Content) {
        Helper.assertNonUiThread()
        progressItem(content, false)
        try {
            ContentHelper.removeContent(applicationContext, dao, content)
            trace(Log.INFO, "Removed item: %s from database and file system.", content.title)
        } catch (cnre: ContentNotProcessedException) {
            nbError++
            trace(Log.WARN, "Error when trying to delete %s", content.id)
            dao.updateContentProcessedFlag(content.id, false)
        } catch (e: Exception) {
            nbError++
            trace(
                Log.WARN,
                "Error when trying to delete %s : %s - %s",
                content.title,
                e.message,
                Helper.getStackTraceString(e)
            )
            dao.updateContentProcessedFlag(content.id, false)
        }
    }

    private fun streamContent(content: Content) {
        Timber.d("Checking pages availability")
        // Reparse content from scratch if images KO
        val res = if (!ContentHelper.isDownloadable(content)) {
            trace(Log.INFO, "Pages unreachable; reparsing content %s", content.title)
            // Reparse content itself
            val newContent = ContentHelper.reparseFromScratch(content)
            if (newContent.isEmpty) {
                dao.updateContentProcessedFlag(content.id, false)
                newContent
            } else {
                val reparsedContent = newContent.get()
                // Reparse pages
                val newImages =
                    ContentHelper.fetchImageURLs(
                        reparsedContent,
                        reparsedContent.galleryUrl,
                        StatusContent.ONLINE
                    )
                reparsedContent.setImageFiles(newImages)
                // Associate new pages' cover with current cover file (that won't be deleted)
                reparsedContent.cover.setStatus(StatusContent.DOWNLOADED).fileUri =
                    content.cover.fileUri
                // Save everything
                dao.replaceImageList(reparsedContent.id, newImages)
                Optional.of<Content>(reparsedContent)
            }
        } else Optional.of<Content>(content)

        if (res.isPresent) {
            dao.selectContent(res.get().id)?.let {
                ContentHelper.purgeFiles(applicationContext, it, false, false)
                // Update content folder and JSON Uri's after purging
                it.downloadMode = Content.DownloadMode.STREAM
                dao.insertContentCore(it)
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
                ContentHelper.updateJson(applicationContext, it)
            }
            trace(Log.INFO, "Streaming succeeded for %s", content.title)
        } else {
            trace(Log.WARN, "Streaming failed for %s", content.title)
        }
    }

    private fun purgeContentList(ids: LongArray, keepCovers: Boolean) {
        // Flag the content as "being deleted" (triggers blink animation; lock operations)
        for (id in ids) dao.updateContentProcessedFlag(id, true)

        // Purge them
        for (id in ids) {
            val c = dao.selectContent(id)
            if (c != null) purgeContentFiles(c, !keepCovers)
            dao.updateContentProcessedFlag(id, false)
            if (isStopped) break
        }
    }

    /**
     * Purge files from the given content
     *
     * @param content Content to be purged
     */
    private fun purgeContentFiles(content: Content, removeCover: Boolean) {
        progressItem(content, true)
        try {
            ContentHelper.purgeFiles(applicationContext, content, false, removeCover)
            // Update content folder and JSON Uri's after purging
            dao.insertContentCore(content)
            trace(Log.INFO, "Purged item: %s.", content.title)
        } catch (e: Exception) {
            nbError++
            Timber.w(e)
            trace(Log.WARN, "Error when trying to purge %s : %s", content.title, e.message)
        }
    }

    private fun removeGroups(ids: LongArray, deleteGroupsOnly: Boolean) {
        val groups = dao.selectGroups(ids)
        try {
            for (g in groups) {
                deleteGroup(g, deleteGroupsOnly)
                if (isStopped) break
            }
        } finally {
            GroupHelper.updateGroupsJson(applicationContext, dao)
        }
    }

    /**
     * Delete the given group
     * WARNING : If the group contains GroupItems, it will be ignored
     * This method is aimed to be used to delete empty groups when using Custom grouping
     *
     * @param group Group to be deleted
     */
    private fun deleteGroup(group: Group, deleteGroupsOnly: Boolean) {
        Helper.assertNonUiThread()
        var theGroup: Group? = group
        progressItem(theGroup, false)
        try {
            // Reassign group for contained items
            if (deleteGroupsOnly) {
                val containedContentList = dao.selectContent(theGroup!!.contentIds.toLongArray())
                for (c in containedContentList) {
                    val movedContent = GroupHelper.moveContentToCustomGroup(
                        c, null,
                        dao
                    )
                    ContentHelper.updateJson(applicationContext, movedContent)
                }
                theGroup = dao.selectGroup(theGroup.id)
            } else if (theGroup!!.grouping == Grouping.DYNAMIC) { // Delete books from dynamic group
                val bundle = ContentSearchBundle()
                bundle.groupId = theGroup.id
                val containedContentList = dao.searchBookIdsUniversal(bundle).toLongArray()
                processContentList(containedContentList, ToolsActivity.MassOperation.DELETE)
            }
            if (theGroup != null) {
                if (!theGroup.items.isEmpty()) {
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

    private fun removeQueue(ids: LongArray) {
        val contents = dao.selectContent(ids)
        try {
            for (c in contents) {
                removeQueuedContent(c)
                if (isStopped) break
            }
        } finally {
            if (ContentHelper.updateQueueJson(applicationContext, dao)) trace(
                Log.INFO,
                "Queue JSON successfully saved"
            ) else trace(
                Log.WARN, "Queue JSON saving failed"
            )
        }
    }

    private fun removeQueuedContent(content: Content) {
        try {
            progressItem(content, false)
            ContentHelper.removeQueuedContent(applicationContext, dao, content, true)
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

    private fun progressItem(item: Any?, isPurge: Boolean) {
        var title: String? = null
        if (item is Content) title = item.title else if (item is Group) title = item.name
        if (title != null) {
            deleteProgress++
            notificationManager.notify(
                DeleteProgressNotification(
                    title,
                    deleteProgress + nbError,
                    deleteMax,
                    isPurge
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

    private fun progressDone() {
        notificationManager.notifyLast(
            DeleteCompleteNotification(
                deleteMax,
                nbError,
                isDownloadPrepurge
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