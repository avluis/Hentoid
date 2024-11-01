package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.notification.appUpdate.UpdateFailedNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateInstallNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateProgressNotification
import me.devsaki.hentoid.util.download.downloadToFile
import me.devsaki.hentoid.util.file.getFileUriCompat
import me.devsaki.hentoid.util.file.legacyFileFromUri
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.data.UpdateDownloadData
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

val APK_MIMETYPE = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk")

class UpdateDownloadWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.update_download_service, null) {

    private var progressPc = 0f

    companion object {
        fun isRunning(context: Context): Boolean {
            return isRunning(context, R.id.update_download_service)
        }
    }

    override fun getStartNotification(): BaseNotification {
        return UpdateProgressNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        // Nothing
    }

    override suspend fun getToWork(input: Data) {
        val data = UpdateDownloadData.Parser(inputData)
        val apkUrl = data.url

        try {
            withContext(Dispatchers.IO) {
                downloadUpdate(apkUrl)
            }
        } catch (e: IOException) {
            Timber.w(e, "Update download failed")
            notificationManager.notifyLast(UpdateFailedNotification(apkUrl))
        }
    }

    @Throws(IOException::class)
    private fun downloadUpdate(apkUrl: String) {
        Timber.d("DOWNLOADING APK")
        val apk = downloadToFile(
            applicationContext,
            Site.NONE,
            apkUrl,
            emptyList(),
            Uri.fromFile(applicationContext.externalCacheDir),
            "hentoid.apk",
            AtomicBoolean(),
            forceMimeType = APK_MIMETYPE,
            resourceId = 0
        ) { it ->
            progressPc = it
            launchProgressNotification()
        }
        apk.first?.let {
            Timber.d("Download successful")
            legacyFileFromUri(it)?.let { file ->
                notificationManager.notifyLast(
                    // Must use getFileUriCompat to avoid being molested by Android
                    UpdateInstallNotification(getFileUriCompat(applicationContext, file))
                )
            }
        } ?: run {
            Timber.d("Download failed")
        }
    }

    override fun runProgressNotification() {
        Timber.v("Download progress: %s%%", progressPc.roundToInt())
        notificationManager.notify(UpdateProgressNotification(progressPc.roundToInt()))
    }
}