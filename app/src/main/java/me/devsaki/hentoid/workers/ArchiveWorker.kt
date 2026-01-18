package me.devsaki.hentoid.workers

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.notification.archive.ArchiveCompleteNotification
import me.devsaki.hentoid.notification.archive.ArchiveProgressNotification
import me.devsaki.hentoid.notification.archive.ArchiveStartNotification
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.canBeArchived
import me.devsaki.hentoid.util.createArchivePdfCover
import me.devsaki.hentoid.util.download.selectDownloadLocation
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.DEFAULT_MIME_TYPE
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.file.createNewDownloadFile
import me.devsaki.hentoid.util.file.findFile
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.formatDisplay
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.getParent
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.file.removeDocument
import me.devsaki.hentoid.util.file.zipFiles
import me.devsaki.hentoid.util.formatFolderName
import me.devsaki.hentoid.util.getOrCreateSiteDownloadDir
import me.devsaki.hentoid.util.getStorageRoot
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.persistJson
import me.devsaki.hentoid.util.removeContent
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant


class ArchiveWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.archive_service, "archive") {

    @JsonClass(generateAdapter = true)
    data class Params(
        val targetFolderUri: String,
        val targetFormat: Int,
        val pdfBackgroundColor: Int,
        val overwrite: Boolean,
        val deleteOnSuccess: Boolean,
        val archivePrimaryContent: Boolean = false
    )

    private var nbItems = 0
    private var nbKO = 0
    private lateinit var globalProgress: ProgressManager

    private lateinit var progressNotification: ArchiveProgressNotification


    override fun getStartNotification(): BaseNotification {
        return ArchiveStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        // Nothing
    }

    override suspend fun getToWork(input: Data) {
        val contentIds = inputData.getLongArray("IDS")
        val paramsStr = inputData.getString("PARAMS")
        require(contentIds != null)
        require(paramsStr != null)
        require(paramsStr.isNotEmpty())

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        val params = moshi.adapter(Params::class.java).fromJson(paramsStr)
        require(params != null)

        archive(contentIds, params)
    }

    private suspend fun archive(contentIds: LongArray, params: Params) =
        withContext(Dispatchers.IO) {
            globalProgress = ProgressManager(contentIds.size)
            nbItems = contentIds.size

            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                dao.updateContentsProcessedFlagById(contentIds.toList(), true)
                for (contentId in contentIds) {
                    if (isStopped) break
                    try {
                        val content = dao.selectContent(contentId)
                        content?.let {
                            if (canBeArchived(content)) archiveContent(content, params, dao)
                            else {
                                dao.updateContentProcessedFlag(it.id, false)
                                globalProgress.setProgress(contentId.toString(), 1f)
                                nextKO()
                            }
                        }
                    } catch (t: Throwable) {
                        Timber.w(t)
                        globalProgress.setProgress(contentId.toString(), 1f)
                        nextKO()
                    }
                }
                if (isStopped) notificationManager.cancel()
                notifyProcessEnd()
            } finally {
                dao.cleanup()
            }
        }

    private suspend fun archiveContent(content: Content, params: Params, dao: CollectionDAO) {
        Timber.i("Archiving %s", content.title)
        val context = applicationContext
        val bookFolder = getDocumentFromTreeUriString(context, content.storageUri) ?: return

        // Archive primary : Get images only
        // Else : Everything (incl. JSON and thumb) gets into the archive
        val filter = if (params.archivePrimaryContent) imageNamesFilter else null
        val files = listFiles(context, bookFolder, filter)
        if (files.isEmpty()) return

        Timber.i("Archive ${content.storageUri} : ${files.size} files to process")

        val destFileUri = getTargetFile(context, content, params)
        Timber.d("DestUri : ${destFileUri.formatDisplay()}")
        val outputStream = getOutputStream(context, destFileUri)
        var success = false
        outputStream?.use { os ->
            if (2 == params.targetFormat) { // PDF
                val mgr = PdfManager()
                val color = when (params.pdfBackgroundColor) {
                    1 -> R.color.light_gray
                    2 -> R.color.dark_gray
                    3 -> R.color.black
                    else -> R.color.white
                }
                mgr.convertImagesToPdf(
                    context,
                    os,
                    files,
                    true,
                    Color.valueOf(ContextCompat.getColor(context, color))
                ) {
                    globalProgress.setProgress(content.id.toString(), it)
                    launchProgressNotification()
                }
                // PDF is not a storage method => no mapping to perform
            } else { // Archive
                context.zipFiles(files, os, this::isStopped) {
                    globalProgress.setProgress(content.id.toString(), it)
                    launchProgressNotification()
                }
                // Map new image locations
                if (params.archivePrimaryContent) {
                    val imgs = content.imageList
                    val imgHash = imgs.groupBy { UriParts(it.fileUri).fileNameFull }
                    files.forEach { f ->
                        imgHash[f.name]?.firstOrNull()?.let { img ->
                            img.fileUri =
                                destFileUri.toString() + File.separator + f.name
                            if (!img.url.startsWith("http")) img.url = img.fileUri
                        }
                    }
                    dao.insertImageFiles(imgs)
                    content.setImageFiles(imgs)
                }
            }
            success = true
        }
        if (success && !isStopped) {
            if (params.archivePrimaryContent) {
                content.storageUri = destFileUri.toString()
                val formerJsonLocation = content.jsonUri.toUri()
                content.jsonUri = ""
                if (persistJson(context, content)) {
                    removeDocument(context, formerJsonLocation)
                    content.lastEditDate = Instant.now().toEpochMilli()
                    content.downloadMode = DownloadMode.DOWNLOAD_ARCHIVE
                    dao.insertContentCore(content)
                    // Remove former location
                    removeDocument(context, bookFolder)
                    // Create thumb
                    createArchivePdfCover(context, content, dao)
                    dao.updateContentProcessedFlag(content.id, false)
                }
            }
            if (params.deleteOnSuccess) removeContent(context, dao, content)
        }
    }

    @Throws(IOException::class)
    private fun getTargetFile(
        context: Context,
        content: Content,
        params: Params
    ): Uri {
        // Build destination file
        val bookFolderName = formatFolderName(content)
        val ext = when (params.targetFormat) {
            1 -> "cbz"
            2 -> "pdf"
            else -> "zip"
        }
        // First try creating the file with the new naming...
        var destName = bookFolderName.first + "." + ext
        // Identify target folder
        val targetFolderUri = params.targetFolderUri.ifEmpty {
            if (StatusContent.EXTERNAL == content.status) {
                Beholder.ignoreFolder(content.storageUri.toUri())
                val storageRoot =
                    content.getStorageRoot() ?: throw IOException("Couldn't locate external folder")
                getParent(context, storageRoot, content.storageUri.toUri())?.toString()
                    ?: throw IOException("Couldn't locate external folder")
            } else {
                val location = selectDownloadLocation(context)
                getOrCreateSiteDownloadDir(context, location, content.site)?.uri?.toString()
                    ?: throw IOException("Couldn't locate site folder")
            }
        }
        return try {
            createTargetFile(context, targetFolderUri, destName, params.overwrite)
        } catch (_: IOException) { // ...if it fails, try creating the file with the old sanitized naming
            destName = bookFolderName.second + "." + ext
            createTargetFile(context, targetFolderUri, destName, params.overwrite)
        }
    }

    /**
     * Returns file Uri; Uri.EMPTY if nothing has been created
     */
    private fun createTargetFile(
        context: Context,
        targetFolderUri: String,
        displayName: String,
        overwrite: Boolean
    ): Uri {
        if (targetFolderUri == Settings.Value.TARGET_FOLDER_DOWNLOADS) {
            // Overwrite can't be taken into account as Android prevents listing
            // what's inside the Downloads folder for security/privacy reasons

            // NB : specifying the ZIP Mime-Type here forces extension to ".zip"
            // even when the file name already ends with ".cbr"
            return createNewDownloadFile(context, displayName, DEFAULT_MIME_TYPE)
        } else {
            getDocumentFromTreeUriString(context, targetFolderUri)?.let { targetFolder ->
                if (!overwrite) {
                    val existing =
                        findFile(context, targetFolder, displayName)
                    // If the target file is already there and we can't overwrite, skip archiving
                    if (existing != null) Uri.EMPTY
                }
                findOrCreateDocumentFile(
                    context,
                    targetFolder,
                    DEFAULT_MIME_TYPE,
                    displayName
                )?.let {
                    return it.uri
                }
            }
        }
        return Uri.EMPTY
    }

    private fun nextKO() {
        nbKO++
        launchProgressNotification()
    }

    override fun runProgressNotification() {
        if (!this::progressNotification.isInitialized) {
            progressNotification =
                ArchiveProgressNotification("", globalProgress.getGlobalProgress())
        } else {
            progressNotification.progress = globalProgress.getGlobalProgress()
        }
        notificationManager.notify(progressNotification)
    }

    private fun notifyProcessEnd() {
        notificationManager.notifyLast(ArchiveCompleteNotification(nbItems, nbKO))
    }
}