package me.devsaki.hentoid.workers

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.R
import me.devsaki.hentoid.notification.appUpdate.UpdateFailedNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateInstallNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateProgressNotification
import me.devsaki.hentoid.util.file.FILE_IO_BUFFER_SIZE
import me.devsaki.hentoid.util.file.getFileUriCompat
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.network.getOnlineResource
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.data.UpdateDownloadData
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class UpdateDownloadWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.update_download_service, null) {

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

    override fun onClear(logFile: DocumentFile?) {
        // Nothing
    }

    override fun getToWork(input: Data) {
        val data = UpdateDownloadData.Parser(inputData)
        val apkUrl = data.url

        try {
            downloadUpdate(apkUrl)
        } catch (e: IOException) {
            Timber.w(e, "Update download failed")
            notificationManager.notifyLast(UpdateFailedNotification(apkUrl))
        }
    }

    @Throws(IOException::class)
    private fun downloadUpdate(apkUrl: String) {
        val context = applicationContext
        Timber.w(context.resources.getString(R.string.starting_download))
        val file = File(context.externalCacheDir, "hentoid.apk")
        file.createNewFile()
        val response = getOnlineResource(apkUrl, null, false, false, false)
        Timber.d("DOWNLOADING APK - RESPONSE %s", response.code)
        if (response.code >= 300) throw IOException("Network error " + response.code)
        val body = response.body
            ?: throw IOException("Could not read response : empty body for $apkUrl")
        var size = body.contentLength()
        if (size < 1) size = 1
        Timber.d("WRITING DOWNLOADED APK TO %s (size %.2f KB)", file.absolutePath, size / 1024.0)
        val buffer = ByteArray(FILE_IO_BUFFER_SIZE)
        var len: Int
        var processed: Long = 0
        var iteration = 0
        body.byteStream().use { `in` ->
            getOutputStream(file).use { out ->
                while (`in`.read(buffer).also { len = it } > -1) {
                    processed += len.toLong()
                    if (0 == ++iteration % 50) // Notify every 200KB
                        updateNotificationProgress((processed * 100f / size).roundToInt())
                    out.write(buffer, 0, len)
                }
                out.flush()
            }
        }
        Timber.d("Download successful")
        notificationManager.notifyLast(
            UpdateInstallNotification(getFileUriCompat(context, file))
        )
    }

    private fun updateNotificationProgress(processPc: Int) {
        Timber.v("Download progress: %s%%", processPc)
        notificationManager.notify(UpdateProgressNotification(processPc))
    }
}