package me.devsaki.hentoid.workers

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.notification.archive.ArchiveCompleteNotification
import me.devsaki.hentoid.notification.archive.ArchiveProgressNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.zipFiles
import me.devsaki.hentoid.util.notification.BaseNotification
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream


class ArchiveWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.archive_service, "archive") {

    @JsonClass(generateAdapter = true)
    data class Params(
        val targetFolderUri: String,
        val targetFormat: Int,
        val overwrite: Boolean,
        val deleteOnSuccess: Boolean
    )

    private val dao: CollectionDAO

    private var totalItems = 0
    private var nbOK = 0
    private var nbKO = 0
    private lateinit var globalProgress: ProgressManager

    init {
        dao = ObjectBoxDAO(context)
    }


    override fun getStartNotification(): BaseNotification {
        return ArchiveProgressNotification("", 0, 0, 0f)
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear() {
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
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

    private fun archive(contentIds: LongArray, params: Params) {
        globalProgress = ProgressManager(contentIds.size)

        for (contentId in contentIds) {
            val content = dao.selectContent(contentId)
            content?.let {
                if (ContentHelper.canBeArchived(content)) archiveContent(content, params)
                else {
                    globalProgress.setProgress(content.id.toString(), 1f)
                    nextKO()
                }
            }
        }
        notifyProcessEnd()
    }

    private fun archiveContent(content: Content, params: Params) {
        Timber.i(">> archive %s", content.title)
        val bookFolder = FileHelper.getDocumentFromTreeUriString(
            applicationContext, content.storageUri
        ) ?: return

        val files = FileHelper.listFiles(
            applicationContext, bookFolder, null
        ) // Everything (incl. JSON and thumb) gets into the archive

        if (files.isNotEmpty()) {
            val success: Boolean
            val destFileResult = getFileResult(content, params)
            val outputStream: OutputStream? = destFileResult.first
            if (outputStream != null) {
                outputStream.use { os ->
                    applicationContext.zipFiles(files, os) { f ->
                        globalProgress.setProgress(content.id.toString(), f)
                        notifyProcessProgress()
                    }
                    success = true
                }
            } else {
                success = destFileResult.second
            }
            if (success && params.deleteOnSuccess) {
                ContentHelper.removeContent(applicationContext, dao, content)
            }
            if (!success) {
                globalProgress.setProgress(content.id.toString(), 1f)
                nextKO()
            } else nextOK()
        }
    }

    private fun getFileResult(content: Content, params: Params): Pair<OutputStream?, Boolean> {
        // Build destination file
        val bookFolderName = ContentHelper.formatBookFolderName(content)
        val ext = if (0 == params.targetFormat) "zip" else "cbz"
        // First try creating the file with the new naming...
        var destName = bookFolderName.first + "." + ext
        return try {
            createTargetFile(params.targetFolderUri, destName, params.overwrite)
        } catch (e: IOException) { // ...if it fails, try creating the file with the old sanitized naming
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
        if (targetFolderUri == Settings.Value.ARCHIVE_TARGET_FOLDER_DOWNLOADS) {
            // Overwrite can't be taken into account as Android prevents listing
            // what's inside the Downloads folder for security/privacy reasons
            return Pair(
                // NB : specifying the ZIP Mime-Type here forces extension to ".zip"
                // even when the file name already ends with ".cbr"
                FileHelper.openNewDownloadOutputStream(
                    applicationContext, displayName, FileHelper.DEFAULT_MIME_TYPE
                ), true
            )
        } else {
            val targetFolder =
                FileHelper.getDocumentFromTreeUriString(applicationContext, targetFolderUri)
            if (targetFolder != null) {
                if (!overwrite) {
                    val existing =
                        FileHelper.findFile(applicationContext, targetFolder, displayName)
                    // If the target file is already there, skip archiving
                    if (existing != null) return Pair(null, true)
                }
                val destFile = FileHelper.findOrCreateDocumentFile(
                    applicationContext, targetFolder, FileHelper.DEFAULT_MIME_TYPE, displayName
                )
                if (destFile != null) return Pair(
                    FileHelper.getOutputStream(
                        applicationContext,
                        destFile
                    ), true
                )
            }
        }
        return Pair(null, false)
    }

    private fun nextOK() {
        nbOK++
        notifyProcessProgress()
    }

    private fun nextKO() {
        nbKO++
        notifyProcessProgress()
    }

    private fun notifyProcessProgress() {
        notificationManager.notify(
            ArchiveProgressNotification(
                "", nbOK + nbKO, totalItems, globalProgress.getGlobalProgress()
            )
        )
    }

    private fun notifyProcessEnd() {
        notificationManager.notifyLast(ArchiveCompleteNotification(nbOK, nbKO))
    }
}