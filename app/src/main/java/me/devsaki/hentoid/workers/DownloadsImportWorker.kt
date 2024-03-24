package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.tools.DownloadsImportDialogFragment.Companion.readFile
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.resumeQueue
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.CloudflareHelper
import me.devsaki.hentoid.util.network.CloudflareHelper.CloudflareProtectedException
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.data.DownloadsImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException

/**
 * Service responsible for importing downloads
 */
class DownloadsImportWorker(
    context: Context,
    parameters: WorkerParameters
) : BaseWorker(context, parameters, R.id.downloads_import_service, "downloads-import") {
    // Variable used during the import process
    private var dao: CollectionDAO? = null
    private var totalItems = 0
    private var cfHelper: CloudflareHelper? = null
    private var nbOK = 0
    private var nbKO = 0

    fun isRunning(context: Context): Boolean {
        return isRunning(context, R.id.downloads_import_service)
    }

    override fun getStartNotification(): BaseNotification {
        return ImportStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear() {
        if (cfHelper != null) cfHelper!!.clear()
        if (dao != null) dao!!.cleanup()
    }

    override fun getToWork(input: Data) {
        val data = DownloadsImportData.Parser(inputData)
        if (data.fileUri.isEmpty()) return
        startImport(
            applicationContext,
            data.fileUri,
            data.queuePosition,
            data.importAsStreamed
        )
    }

    /**
     * Import books from external folder
     */
    private fun startImport(
        context: Context,
        fileUri: String,
        queuePosition: Int,
        importAsStreamed: Boolean
    ) {
        val file = FileHelper.getFileFromSingleUriString(context, fileUri)
        if (null == file) {
            trace(Log.ERROR, "Couldn't find downloads file at %s", fileUri)
            return
        }
        val downloads = readFile(context, file)
        if (downloads.isEmpty()) {
            trace(Log.ERROR, "Downloads file %s is empty", fileUri)
            return
        }
        totalItems = downloads.size
        dao = ObjectBoxDAO()
        try {
            for (s in downloads) {
                var galleryUrl = s
                if (StringHelper.isNumeric(galleryUrl)) galleryUrl = Content.getGalleryUrlFromId(
                    Site.NHENTAI,
                    galleryUrl
                ) // We assume any launch code is Nhentai's
                importGallery(galleryUrl, queuePosition, importAsStreamed, false)
            }
        } catch (ie: InterruptedException) {
            Timber.e(ie)
            Thread.currentThread().interrupt()
        }
        if (Preferences.isQueueAutostart()) resumeQueue(
            applicationContext
        )
        notifyProcessEnd()
    }

    @Throws(InterruptedException::class)
    private fun importGallery(
        url: String,
        queuePosition: Int,
        importAsStreamed: Boolean,
        hasPassedCf: Boolean
    ) {
        val site = Site.searchByUrl(url)
        if (null == site || Site.NONE == site) {
            trace(Log.WARN, "ERROR : Unsupported source @ %s", url)
            nextKO(applicationContext, null)
            return
        }
        val existingContent =
            dao!!.selectContentBySourceAndUrl(site, Content.transformRawUrl(site, url), null)
        if (existingContent != null) {
            val location =
                if (ContentHelper.isInQueue(existingContent.status)) "queue" else "library"
            trace(Log.INFO, "ERROR : Content already in %s @ %s", location, url)
            nextKO(applicationContext, null)
            return
        }
        try {
            val content = ContentHelper.parseFromScratch(url)
            if (content.isEmpty) {
                trace(Log.WARN, "ERROR : Unreachable content @ %s", url)
                nextKO(applicationContext, null)
            } else {
                trace(Log.INFO, "Added content @ %s", url)
                val c = content.get()
                c.downloadMode =
                    if (importAsStreamed) Content.DownloadMode.STREAM else Content.DownloadMode.DOWNLOAD
                dao!!.addContentToQueue(
                    c,
                    null,
                    null,
                    queuePosition,
                    -1,
                    null,
                    isQueueActive(applicationContext)
                )
                nextOK(applicationContext)
            }
        } catch (e: IOException) {
            trace(Log.WARN, "ERROR : While loading content @ %s", url)
            nextKO(applicationContext, e)
        } catch (cpe: CloudflareProtectedException) {
            if (hasPassedCf) {
                trace(Log.WARN, "Cloudflare bypass ineffective for content @ %s", url)
                nextKO(applicationContext, null)
                return
            }
            trace(Log.INFO, "Trying to bypass Cloudflare for content @ %s", url)
            if (null == cfHelper) cfHelper = CloudflareHelper()
            if (cfHelper!!.tryPassCloudflare(site, null)) {
                importGallery(url, queuePosition, importAsStreamed, true)
            } else {
                trace(Log.WARN, "Cloudflare bypass failed for content @ %s", url)
                nextKO(applicationContext, null)
            }
        }
    }

    private fun nextOK(context: Context) {
        nbOK++
        notifyProcessProgress(context)
    }

    private fun nextKO(context: Context, e: Throwable?) {
        nbKO++
        if (e != null) Timber.w(e)
        notifyProcessProgress(context)
    }

    private fun notifyProcessProgress(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Main) {
                doNotifyProcessProgress(context)
            }
        }
    }

    private fun doNotifyProcessProgress(context: Context) {
        notificationManager.notify(
            ImportProgressNotification(
                context.resources.getString(R.string.importing_downloads),
                nbOK + nbKO,
                totalItems
            )
        )
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                R.id.import_downloads,
                0,
                nbOK,
                nbKO,
                totalItems
            )
        )
    }

    private fun notifyProcessEnd() {
        notificationManager.notify(ImportCompleteNotification(nbOK, nbKO))
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.import_downloads,
                0,
                nbOK,
                nbKO,
                totalItems
            )
        )
    }
}