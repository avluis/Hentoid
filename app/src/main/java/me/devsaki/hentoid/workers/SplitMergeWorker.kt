package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.IdRes
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.splitMerge.SplitMergeCompleteNotification
import me.devsaki.hentoid.notification.splitMerge.SplitMergeProgressNotification
import me.devsaki.hentoid.notification.splitMerge.SplitMergeStartNotification
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.createJson
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.getLocation
import me.devsaki.hentoid.util.getOrCreateContentDownloadDir
import me.devsaki.hentoid.util.mergeContents
import me.devsaki.hentoid.util.moveContentToCustomGroup
import me.devsaki.hentoid.util.network.getExtensionFromUri
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.removeContent
import me.devsaki.hentoid.workers.data.SplitMergeData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.time.Instant
import kotlin.math.floor
import kotlin.math.log10

enum class SplitMergeType {
    SPLIT, MERGE
}

/**
 * Worker responsible for deleting content in the background
 */
abstract class BaseSplitMergeWorker(
    context: Context,
    @IdRes serviceId: Int,
    parameters: WorkerParameters
) : BaseWorker(context, parameters, serviceId, "split_merge") {

    // Common params
    private val operationType: SplitMergeType
    private val contentIds: LongArray
    private val deleteAfterOperation: Boolean

    // Merge params
    private val newTitle: String
    private val useBookAsChapter: Boolean

    // Split params
    private val chapterSplitIds: LongArray


    private var nbMax = 0
    private var nbProgress = 0
    private var nbError = 0

    private val dao: CollectionDAO

    init {
        val inputData = SplitMergeData.Parser(inputData)
        operationType = if (0 == inputData.operation) SplitMergeType.SPLIT else SplitMergeType.MERGE
        contentIds = inputData.contentIds
        deleteAfterOperation = inputData.deleteAfterOps
        newTitle = inputData.newTitle
        useBookAsChapter = inputData.useBooksAsChapters
        chapterSplitIds = inputData.chapterIdsForSplit

        dao = ObjectBoxDAO()

        nbMax = contentIds.size
    }

    override fun getStartNotification(): BaseNotification {
        return SplitMergeStartNotification(nbMax, operationType)
    }

    override fun onInterrupt() {
        // Nothing to do here
    }

    override fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
        nbProgress = 0
        nbError = 0

        if (contentIds.isEmpty()) return

        if (SplitMergeType.SPLIT == operationType) split(contentIds[0])
        else merge()
    }

    private fun split(contentId: Long) {
        val content = dao.selectContent(contentId) ?: return
        val chapters = dao.selectChapters(chapterSplitIds.toList())
        var targetFolder : DocumentFile? = null

        val images = content.imageList
        if (chapters.isEmpty()) throw ContentNotProcessedException(content, "No chapters detected")
        if (images.isEmpty()) throw ContentNotProcessedException(content, "No images detected")
        nbMax = chapters.flatMap { it.imageList }.count { it.isReadable }
        for (chap in chapters) {
            if (isStopped) break
            val splitContent = createContentFromChapter(content, chap)

            // Create a new folder for the split content
            val location = getLocation(content)
            targetFolder = getOrCreateContentDownloadDir(
                applicationContext,
                splitContent,
                location,
                true
            )
            if (null == targetFolder || !targetFolder.exists())
                throw ContentNotProcessedException(
                    splitContent,
                    "Could not create target directory"
                )
            splitContent.setStorageDoc(targetFolder)

            // Copy the corresponding images to that folder
            val splitContentImages = splitContent.imageList
            for (img in splitContentImages) {
                if (isStopped) break
                if (img.status == StatusContent.DOWNLOADED) {
                    val extension = getExtensionFromUri(img.fileUri)
                    val newUri = copyFile(
                        applicationContext,
                        Uri.parse(img.fileUri),
                        targetFolder.uri,
                        img.mimeType,
                        img.name + "." + extension
                    )
                    if (newUri != null) img.fileUri = newUri.toString()
                    else Timber.w("Could not move file %s", img.fileUri)

                    progressPlus(chap.name)
                }
            }
            if (isStopped) break

            // Save the JSON for the new book
            val jsonFile = createJson(applicationContext, splitContent)
            if (jsonFile != null) splitContent.jsonUri = jsonFile.uri.toString()

            // Save new content (incl. onn-custom group operations)
            addContent(applicationContext, dao, splitContent)

            // Set custom group, if any
            val customGroups = content.getGroupItems(Grouping.CUSTOM)
            if (customGroups.isNotEmpty())
                moveContentToCustomGroup(splitContent, customGroups[0].getGroup(), dao)
        }
        progressDone(chapterSplitIds.size)

        // Remove latest target folder and split images if manually canceled
        if (isStopped) {
            targetFolder?.delete()
        }

        // If we're here, no exception has been triggered -> cleanup if needed
        if (deleteAfterOperation && !isStopped) {
            // TODO delete selected chapters and associated files
        }
    }

    private fun merge() {
        val contentList = dao.selectContent(contentIds)
        val removedContents: MutableSet<Long> = HashSet()
        if (contentList.isEmpty()) return

        // Flag the content as "being deleted" (triggers blink animation)
        if (deleteAfterOperation)
            contentList.forEach { dao.updateContentProcessedFlag(it.id, true) }

        nbMax = contentList.flatMap { it.imageList }.count { it.isReadable }
        try {
            mergeContents(
                applicationContext,
                contentList,
                newTitle,
                useBookAsChapter,
                dao,
                this::isStopped,
                { _, _, s -> progressPlus(s) },
                { progressDone(contentList.size) }
            )
            // If we're here, no exception has been triggered -> cleanup if asked
            if (deleteAfterOperation && !isStopped) {
                contentList.forEach { c ->
                    try {
                        removeContent(applicationContext, dao, c)
                        removedContents.add(c.id)
                        trace(
                            Log.INFO,
                            "Removed item: %s from database and file system.",
                            c.title
                        )
                    } catch (e: Exception) {
                        Timber.w(e)
                        trace(Log.WARN, "Error when trying to delete %s", c.id)
                        dao.updateContentProcessedFlag(c.id, false)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
        // Reset the "marked as being processed" flag for un-deleted content
        if (deleteAfterOperation)
            contentList.forEach {
                if (!removedContents.contains(it.id)) dao.updateContentProcessedFlag(it.id, false)
            }
    }


    private fun createContentFromChapter(content: Content, chapter: Chapter): Content {
        val splitContent = Content()
        var url = chapter.url
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
                img.isCover = (0 == position)
                img.order = position
                img.computeName(nbMaxDigits)
            }
            splitContent.setImageFiles(images)
            splitContent.setChapters(null)
            splitContent.qtyPages = images.count { imf -> imf.isReadable }
            splitContent.computeSize()
            var coverImageUrl = images[0].url
            if (coverImageUrl.isEmpty()) coverImageUrl = content.coverImageUrl
            splitContent.coverImageUrl = coverImageUrl
        }
        val splitAttributes = listOf(content).flatMap { c -> c.attributes }
        splitContent.addAttributes(splitAttributes)
        return splitContent
    }

    private fun progressPlus(bookTitle: String) {
        nbProgress++
        notificationManager.notify(
            SplitMergeProgressNotification(
                bookTitle,
                nbProgress + nbError,
                nbMax,
                operationType
            )
        )
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                if (SplitMergeType.SPLIT == operationType) R.id.split_service else R.id.merge_service,
                0,
                nbProgress,
                nbError,
                nbMax
            )
        )
    }

    private fun progressDone(nbBooksDone: Int) {
        notificationManager.notifyLast(
            SplitMergeCompleteNotification(
                nbBooksDone,
                nbError,
                operationType
            )
        )
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                if (SplitMergeType.SPLIT == operationType) R.id.split_service else R.id.merge_service,
                0,
                nbProgress,
                nbError,
                nbBooksDone
            )
        )
    }
}

class SplitWorker(context: Context, parameters: WorkerParameters) :
    BaseSplitMergeWorker(context, R.id.split_service, parameters)

class MergeWorker(context: Context, parameters: WorkerParameters) :
    BaseSplitMergeWorker(context, R.id.merge_service, parameters)