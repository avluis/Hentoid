package me.devsaki.hentoid.workers

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
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
import me.devsaki.hentoid.notification.archive.ArchiveCompleteNotification
import me.devsaki.hentoid.notification.archive.ArchiveProgressNotification
import me.devsaki.hentoid.notification.archive.ArchiveStartNotification
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.canBeArchived
import me.devsaki.hentoid.util.file.DEFAULT_MIME_TYPE
import me.devsaki.hentoid.util.file.findFile
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.file.openNewDownloadOutputStream
import me.devsaki.hentoid.util.file.zipFiles
import me.devsaki.hentoid.util.formatFolderName
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.removeContent
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream


class ArchiveWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.archive_service, "archive") {

    @JsonClass(generateAdapter = true)
    data class Params(
        val targetFolderUri: String,
        val targetFormat: Int,
        val pdfBackgroundColor: Int,
        val overwrite: Boolean,
        val deleteOnSuccess: Boolean
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

            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                for (contentId in contentIds) {
                    if (isStopped) break
                    val content = dao.selectContent(contentId)
                    content?.let {
                        if (canBeArchived(content)) archiveContent(content, params, dao)
                        else {
                            globalProgress.setProgress(content.id.toString(), 1f)
                            nextKO()
                        }
                    }
                }
                if (isStopped) notificationManager.cancel()
                notifyProcessEnd()
            } finally {
                dao.cleanup()
            }
        }

    private suspend fun archiveContent(content: Content, params: Params, dao: CollectionDAO) {
        Timber.i(">> archive %s", content.title)
        val bookFolder = getDocumentFromTreeUriString(
            applicationContext, content.storageUri
        ) ?: return

        // Everything (incl. JSON and thumb) gets into the archive
        val files = listFiles(applicationContext, bookFolder, null)
        nbItems = files.size

        if (files.isNotEmpty()) {
            val destFileResult = getFileResult(content, params)
            val outputStream: OutputStream? = destFileResult.first
            val success = outputStream?.use { os ->
                if (2 == params.targetFormat) { // PDF
                    val mgr = PdfManager()
                    val color = when (params.pdfBackgroundColor) {
                        1 -> R.color.light_gray
                        2 -> R.color.dark_gray
                        3 -> R.color.black
                        else -> R.color.white
                    }
                    mgr.convertImagesToPdf(
                        applicationContext,
                        os,
                        files,
                        true,
                        Color.valueOf(ContextCompat.getColor(applicationContext, color))
                    ) {
                        globalProgress.setProgress(content.id.toString(), it)
                        launchProgressNotification()
                    }
                } else {
                    applicationContext.zipFiles(files, os, this::isStopped) {
                        globalProgress.setProgress(content.id.toString(), it)
                        launchProgressNotification()
                    }
                }
                !isStopped
            } ?: run {
                destFileResult.second
            }
            if (success && params.deleteOnSuccess && !isStopped) {
                removeContent(applicationContext, dao, content)
            }
        }
    }

    private fun getFileResult(content: Content, params: Params): Pair<OutputStream?, Boolean> {
        // Build destination file
        val bookFolderName = formatFolderName(content)
        val ext = when (params.targetFormat) {
            1 -> "cbz"
            2 -> "pdf"
            else -> "zip"
        }
        // First try creating the file with the new naming...
        var destName = bookFolderName.first + "." + ext
        return try {
            createTargetFile(params.targetFolderUri, destName, params.overwrite)
        } catch (_: IOException) { // ...if it fails, try creating the file with the old sanitized naming
            destName = bookFolderName.second + "." + ext
            createTargetFile(params.targetFolderUri, destName, params.overwrite)
        }
    }

    /**
     * Returns
     *  - Output stream; null if none
     *  - Success or failure
     */
    private fun createTargetFile(
        targetFolderUri: String,
        displayName: String,
        overwrite: Boolean
    ): Pair<OutputStream?, Boolean> {
        if (targetFolderUri == Settings.Value.TARGET_FOLDER_DOWNLOADS) {
            // Overwrite can't be taken into account as Android prevents listing
            // what's inside the Downloads folder for security/privacy reasons
            return Pair(
                // NB : specifying the ZIP Mime-Type here forces extension to ".zip"
                // even when the file name already ends with ".cbr"
                openNewDownloadOutputStream(
                    applicationContext, displayName, DEFAULT_MIME_TYPE
                ), true
            )
        } else {
            val targetFolder = getDocumentFromTreeUriString(applicationContext, targetFolderUri)
            if (targetFolder != null) {
                if (!overwrite) {
                    val existing =
                        findFile(applicationContext, targetFolder, displayName)
                    // If the target file is already there, skip archiving
                    if (existing != null) return Pair(null, true)
                }
                val destFile = findOrCreateDocumentFile(
                    applicationContext, targetFolder, DEFAULT_MIME_TYPE, displayName
                )
                if (destFile != null) return Pair(
                    getOutputStream(
                        applicationContext,
                        destFile
                    ), true
                )
            }
        }
        return Pair(null, false)
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