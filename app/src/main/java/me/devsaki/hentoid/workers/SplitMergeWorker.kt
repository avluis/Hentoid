package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.IdRes
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
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
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.createJson
import me.devsaki.hentoid.util.deleteChapters
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.copyFiles
import me.devsaki.hentoid.util.file.extractArchiveEntriesBlocking
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.getLocation
import me.devsaki.hentoid.util.getOrCreateContentDownloadDir
import me.devsaki.hentoid.util.image.PdfManager
import me.devsaki.hentoid.util.image.clearCoilCache
import me.devsaki.hentoid.util.mergeContents
import me.devsaki.hentoid.util.moveContentToCustomGroup
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.persistJson
import me.devsaki.hentoid.util.removeContent
import me.devsaki.hentoid.util.renumberChapters
import me.devsaki.hentoid.workers.data.SplitMergeData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.math.floor
import kotlin.math.log10

enum class SplitMergeType {
    SPLIT, MERGE, REORDER
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
    private val chapterIds: LongArray
    private val deleteAfterOperation: Boolean

    // Merge params
    private val newTitle: String
    private val useBookAsChapter: Boolean

    // Split params
    private val chapterSplitIds: LongArray


    private var nbMax = 0
    private var nbProgress = 0
    private var nbError = 0
    private var bookTitle = ""
    private lateinit var progressNotification: SplitMergeProgressNotification

    private val dao: CollectionDAO

    init {
        val inputData = SplitMergeData.Parser(inputData)
        operationType = SplitMergeType.entries[inputData.operation]
        contentIds = inputData.contentIds
        chapterIds = inputData.chapterIds
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

    override suspend fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
    }

    override suspend fun getToWork(input: Data) {
        nbProgress = 0
        nbError = 0

        when (operationType) {
            SplitMergeType.SPLIT -> split(contentIds.first())
            SplitMergeType.MERGE -> merge()
            SplitMergeType.REORDER -> reorderChapters()
        }
    }

    private suspend fun split(contentId: Long) {
        if (contentIds.isEmpty()) return
        val content = dao.selectContent(contentId) ?: return
        val chapters = dao.selectChapters(chapterSplitIds.toList())
        var targetFolder: DocumentFile? = null

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
                true,
                content.storageUri.toUri()
            )
            if (null == targetFolder || !targetFolder.exists())
                throw ContentNotProcessedException(
                    splitContent,
                    "Could not create target directory"
                )
            splitContent.setStorageDoc(targetFolder)
            // Ignore the new folder as it is being splitted
            Beholder.ignoreFolder(targetFolder)

            // Copy the corresponding images to that folder
            val splitContentImages =
                splitContent.imageList.filter { it.status == StatusContent.DOWNLOADED || it.status == StatusContent.EXTERNAL }
                    .distinctBy { it.url }
            withContext(Dispatchers.IO) {
                try {
                    if (content.isArchive || content.isPdf) {
                        val sourceFile =
                            getFileFromSingleUriString(applicationContext, content.storageUri)
                                ?: return@withContext

                        val extractInstructions = splitContentImages.map {
                            Pair(
                                it.url.replace(content.storageUri + File.separator, ""),
                                it.order.toLong()
                            )
                        }

                        val extractedFiles = if (content.isArchive) {
                            applicationContext.extractArchiveEntriesBlocking(
                                sourceFile.uri,
                                targetFolder.uri,
                                extractInstructions,
                                this@BaseSplitMergeWorker::launchProgressNotification,
                                this@BaseSplitMergeWorker::isStopped
                            )
                        } else { // PDF
                            val mgr = PdfManager()
                            mgr.extractImagesBlocking(
                                applicationContext,
                                sourceFile,
                                targetFolder.uri,
                                extractInstructions,
                                this@BaseSplitMergeWorker::launchProgressNotification,
                                this@BaseSplitMergeWorker::isStopped
                            )
                        }
                        if (isStopped) return@withContext
                        Timber.d("Successfuly extracted ${extractedFiles.size} / ${splitContentImages.size} files for ${splitContent.title}")
                        if (extractedFiles.size != splitContentImages.size) return@withContext

                        // Map new, copied files Uris
                        splitContentImages.forEachIndexed { index, i ->
                            i.fileUri = extractedFiles[index].toString()
                        }
                        Timber.d("Mapping done for ${splitContent.title}")
                    } else {
                        copyFiles(
                            applicationContext,
                            splitContentImages.map { Pair(it.fileUri.toUri(), it.name) },
                            targetFolder.uri,
                            isCanceled = this@BaseSplitMergeWorker::isStopped,
                            onProgress = { _, oldUri, newUri ->
                                if (newUri != null) {
                                    // Map new, copied files Uris
                                    splitContentImages.firstOrNull { it.fileUri == oldUri.toString() }?.fileUri =
                                        newUri.toString()
                                } else Timber.w("Could not move file $oldUri")
                                bookTitle = chap.name
                                launchProgressNotification()
                            }
                        )
                        Timber.d("Successfuly copied ${splitContentImages.size} files for ${splitContent.title}")
                    }
                    if (isStopped) return@withContext

                    // Save the JSON for the new book
                    val jsonFile = createJson(applicationContext, splitContent)
                    if (jsonFile != null) splitContent.jsonUri = jsonFile.uri.toString()

                    // Save new content (incl. non-custom group operations)
                    addContent(applicationContext, dao, splitContent)
                } finally {
                    dao.cleanup()
                }
            }
            if (isStopped) break

            // Set custom group, if any
            val customGroups = content.getGroupItems(Grouping.CUSTOM)
            if (customGroups.isNotEmpty())
                moveContentToCustomGroup(splitContent, customGroups[0].linkedGroup, dao)
        } // Chapters loop

        // Remove latest target folder and split images if manually canceled
        if (isStopped) {
            withContext(Dispatchers.IO) { targetFolder?.delete() }
        } else {
            progressDone(chapterSplitIds.size)
        }

        // If we're here, no exception has been triggered -> cleanup if needed
        if (deleteAfterOperation && !isStopped) {
            deleteChapters(applicationContext, dao, chapterSplitIds.toList())
        }
    }

    private suspend fun merge() {
        if (contentIds.isEmpty()) return
        val contentList = dao.selectContent(contentIds)
        if (contentList.isEmpty()) return

        // Flag the content as "being deleted" (triggers blink animation)
        if (deleteAfterOperation) dao.updateContentsProcessedFlag(contentList, true)

        val removedContents: MutableSet<Long> = HashSet()
        nbMax = contentList.flatMap { it.imageList }.count { it.isReadable }
        try {
            mergeContents(
                applicationContext,
                contentList,
                newTitle,
                useBookAsChapter,
                dao,
                this::isStopped,
                { _, _, s ->
                    bookTitle = s
                    launchProgressNotification()
                },
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
        if (deleteAfterOperation) {
            val toProcess = contentList.map { it.id }.filterNot { removedContents.contains(it) }
            dao.updateContentsProcessedFlagById(toProcess, false)
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
            images = chapter.imageList.sortedBy { it.order }
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
            splitContent.qtyPages = images.count { it.isReadable }
            splitContent.computeSize()
            var coverImageUrl = images[0].url
            if (coverImageUrl.isEmpty()) coverImageUrl = content.coverImageUrl
            splitContent.coverImageUrl = coverImageUrl
        }
        val splitAttributes = listOf(content).flatMap { it.attributes }
        splitContent.addAttributes(splitAttributes)
        return splitContent
    }

    private suspend fun reorderChapters() {
        if (chapterIds.size < 2) return
        val content = dao.selectChapter(chapterIds.first())
        val contentId = content?.contentId ?: return

        var chapters = dao.selectChapters(contentId)
        require(chapters.isNotEmpty()) { "No chapters found" }

        // Reorder chapters according to target order
        val orderById = chapterIds.withIndex().associate { (index, it) -> it to index }
        chapters = chapters.sortedBy { orderById[it.id] }.toMutableList()

        // Renumber all chapters and update the DB
        renumberChapters(chapters.asSequence())
        dao.insertChapters(chapters)

        // Renumber all readable images
        val orderedImages =
            chapters.map { it.imageFiles }.flatMap { it.toList() }.filter { it.isReadable }
        require(orderedImages.isNotEmpty()) { "No images found" }

        // Keep existing formatting
        val nbMaxDigits = orderedImages.maxOf { it.name.length }
        // Key = source Uri
        // Value = operation
        val operations = HashMap<String, FileOperation>()
        orderedImages.forEachIndexed { index, img ->
            img.order = index + 1
            img.computeName(nbMaxDigits)

            val uriParts = UriParts(Uri.decode(img.fileUri))
            val sourceFileName = uriParts.entireFileName
            val targetFileName = img.name + "." + uriParts.extension
            // Only post actual renaming jobs
            if (!sourceFileName.equals(targetFileName, true)) {
                operations[img.fileUri] = FileOperation(img.fileUri, targetFileName, img)
            }
        }

        Timber.d("Recap")
        operations.forEach { Timber.d("${it.value.targetName} <- ${it.value.sourceUri}") }

        // Groups swaps into permutation groups
        val parentFolder = getDocumentFromTreeUriString(
            applicationContext, chapters[0].content.target.storageUri
        ) ?: throw IOException("Parent folder not found")

        buildPermutationGroups(applicationContext, operations, parentFolder)
        val finalOpsTmp = operations.values.sortedBy { it.sequenceNumber }.sortedBy { it.order }
        val finalOps = finalOpsTmp.groupBy { it.sequenceNumber }.values.toList()

        finalOps.forEach { seq ->
            seq.forEach { op ->
                Timber.d(
                    "[%d.%d] %s <- %s",
                    op.sequenceNumber,
                    op.order,
                    op.targetName,
                    op.sourceUri
                )
            }
        }

        nbMax = finalOps.size

        // Perform swaps by exchanging file content
        // NB : "thanks to" SAF; this works faster than renaming the files :facepalm:
        performSwaps(
            finalOps,
            contentId,
            content.name,
            orderedImages
        )
        progressDone(nbMax)

        // Reset Coil cache as it gets confused by the swapping
        clearCoilCache(applicationContext)
    }

    private suspend fun performSwaps(
        finalOps: List<List<FileOperation>>,
        contentId: Long,
        contentName: String,
        orderedImages: List<ImageFile>
    ) = withContext(Dispatchers.IO) {
        var firstFileContent: ByteArray? = null
        try {
            finalOps.forEach { seq ->
                seq.forEachIndexed { idx, op ->
                    if (op.isLoop) {
                        if (0 == idx) { // First item
                            getInputStream(applicationContext, op.target!!).use {
                                firstFileContent = it.readBytes()
                            }
                        } else if (idx == seq.size - 1 && firstFileContent != null) { // Last item
                            if (BuildConfig.DEBUG) Timber.d(
                                "[%d.%d] Swap %s <- %s (last)",
                                op.sequenceNumber,
                                op.order,
                                op.targetName,
                                op.sourceUri
                            )
                            getOutputStream(applicationContext, op.target!!).use { os ->
                                os?.write(firstFileContent)
                            }
                            op.targetData.fileUri = op.target?.uri?.toString() ?: ""
                            return@forEachIndexed
                        }
                    }

                    if (op.isRename) {
                        if (BuildConfig.DEBUG) Timber.d(
                            "[%d.%d] Rename %s <- %s",
                            op.sequenceNumber,
                            op.order,
                            op.targetName,
                            op.sourceUri
                        )
                        op.source?.renameTo(op.targetName)
                        op.targetData.fileUri = op.source?.uri?.toString() ?: ""
                    } else {
                        if (BuildConfig.DEBUG) Timber.d(
                            "[%d.%d] Swap %s <- %s",
                            op.sequenceNumber,
                            op.order,
                            op.targetName,
                            op.sourceUri
                        )
                        getOutputStream(applicationContext, op.target!!)?.use { os ->
                            getInputStream(applicationContext, op.source!!).use { input ->
                                copy(input, os)
                            }
                        }
                        op.targetData.fileUri = op.target?.uri?.toString() ?: ""
                    }
                }
                bookTitle = contentName
                launchProgressNotification()
            }

            // Finalize
            dao.insertImageFiles(orderedImages)
            val finalContent = dao.selectContent(contentId)
            if (finalContent != null) persistJson(applicationContext, finalContent)
        } finally {
            dao.cleanup()
        }
    }

    /**
     * Enrich the given operations
     *
     * @param ctx Context to use
     * @param ops Operations to enrhich
     *      Key = source file Uri
     *      Value = operation
     * @param root Root of the content folder to use
     */
    private fun buildPermutationGroups(
        ctx: Context,
        ops: MutableMap<String, FileOperation>,
        root: DocumentFile
    ) {
        if (ops.isEmpty()) return

        // Take a snapshot of the Content's current files to simulate operations as they're built
        val files: Map<String, Pair<DocumentFile, String>> = listFiles(ctx, root, null)
            .associateBy({ it.uri.toString() }, { Pair(it, it.name ?: "") })
        if (files.isEmpty()) return

        val availableFiles =
            files.values.map { it.second }.filterNot { it.isEmpty() }.toMutableSet()

        // source name => source Uri
        val uriByName = files.values.associateBy({ it.second }, { it.first.uri.toString() })
            .filterNot { it.key.isEmpty() }
        // target name => source name
        val nameOpsByTarget =
            ops.values.associateBy({ it.targetName }, { files[it.sourceUri]?.second ?: "" })
                .filterNot { it.value.isEmpty() }
                .toMutableMap()
        var sequenceNumber = 0

        while (nameOpsByTarget.isNotEmpty()) {
            val sourceNames = nameOpsByTarget.values.toSet()
            val targetNames = nameOpsByTarget.keys

            // Find a target that is not referenced among the sources (= no permutation loop)
            var isLoop = false
            val startSourceCandidate = targetNames.firstOrNull { !sourceNames.contains(it) }

            // If none, take the 1st element and prepare for doing a permutation loop
            var source: String? = if (null == startSourceCandidate) {
                isLoop = true
                sourceNames.first()
            } else startSourceCandidate

            // Walk the permutation sequence from start to finish
            var order = 0
            while (source != null) {
                // Enrich the corresponding operation
                ops[uriByName[source]]?.let { operation ->
                    operation.source = files[uriByName[source]]?.first
                    operation.target = files[uriByName[operation.targetName]]?.first
                    operation.isRename = !availableFiles.contains(operation.targetName)
                    operation.order = order
                    operation.sequenceNumber = sequenceNumber
                    operation.isLoop = isLoop

                    // Update available files
                    if (operation.isRename) {
                        availableFiles.remove(source)
                        availableFiles.add(operation.targetName)
                    }

                    // Remove the operation we've just recorded
                    nameOpsByTarget.remove(operation.targetName)
                }

                // Next permutation is the one that uses the current source as a target
                source = nameOpsByTarget[source]
                order++
            }
            sequenceNumber++
        }
    }

    override fun runProgressNotification() {
        nbProgress++
        if (!this::progressNotification.isInitialized) {
            progressNotification = SplitMergeProgressNotification(
                bookTitle,
                nbProgress + nbError,
                nbMax,
                operationType
            )
        } else {
            progressNotification.progress = nbProgress + nbError
        }
        if (isStopped) return
        notificationManager.notify(progressNotification)
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                when (operationType) {
                    SplitMergeType.SPLIT -> R.id.split_service
                    SplitMergeType.MERGE -> R.id.merge_service
                    SplitMergeType.REORDER -> R.id.generic_progress
                },
                0,
                nbProgress,
                nbError,
                nbMax
            )
        )
    }

    private fun progressDone(nbBooksDone: Int) {
        if (isStopped) return
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
                when (operationType) {
                    SplitMergeType.SPLIT -> R.id.split_service
                    SplitMergeType.MERGE -> R.id.merge_service
                    SplitMergeType.REORDER -> R.id.generic_progress
                },
                0,
                nbProgress,
                nbError,
                nbBooksDone
            )
        )
    }

    private data class FileOperation(
        val sourceUri: String,
        val targetName: String,
        val targetData: ImageFile,
        // Following fields valued during 2nd pass (buildPermutationGroups)
        var source: DocumentFile? = null,
        var target: DocumentFile? = null,
        var isRename: Boolean = false,
        var order: Int = -1,
        var sequenceNumber: Int = -1,
        var isLoop: Boolean = false
    )
}

class SplitWorker(context: Context, parameters: WorkerParameters) :
    BaseSplitMergeWorker(context, R.id.split_service, parameters)

class MergeWorker(context: Context, parameters: WorkerParameters) :
    BaseSplitMergeWorker(context, R.id.merge_service, parameters)

class ReorderWorker(context: Context, parameters: WorkerParameters) :
    BaseSplitMergeWorker(context, R.id.reorder_service, parameters)