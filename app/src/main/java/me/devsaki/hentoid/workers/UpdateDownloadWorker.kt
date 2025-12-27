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
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.appUpdate.UpdateFailedNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateInstallNotification
import me.devsaki.hentoid.notification.appUpdate.UpdateProgressNotification
import me.devsaki.hentoid.util.download.downloadToFile
import me.devsaki.hentoid.util.file.getFileUriCompat
import me.devsaki.hentoid.util.file.legacyFileFromUri
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.data.UpdateDownloadData
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

val APK_MIMETYPE = MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk")

class UpdateDownloadWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.update_download_service, null) {

    private var progressPc = 0f
    private val killSwitch = AtomicBoolean(false)

    companion object {
        fun isRunning(context: Context): Boolean {
            return isRunning(context, R.id.update_download_service)
        }
    }

    init {
        EventBus.getDefault().register(this)
    }

    override fun getStartNotification(): BaseNotification {
        return UpdateProgressNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        EventBus.getDefault().unregister(this)
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
    private suspend fun downloadUpdate(apkUrl: String) {
        Timber.d("DOWNLOADING APK")
        EventBus.getDefault().post(
            ProcessEvent(ProcessEvent.Type.PROGRESS, R.id.update_download_service)
        )
        val apk = downloadToFile(
            applicationContext,
            Site.NONE,
            apkUrl,
            emptyList(),
            Uri.fromFile(applicationContext.externalCacheDir),
            "hentoid.apk",
            isCanceled = { killSwitch.get() },
            resourceId = 0,
            forceMimeType = APK_MIMETYPE
        ) {
            progressPc = it
            if (0 == (progressPc.roundToInt() % 5)) launchProgressNotification()
        }

        if (killSwitch.get()) {
            notificationManager.cancel()
            return
        }

        apk?.let {
            Timber.d("Download successful")
            legacyFileFromUri(it)?.let { file ->
                // Must use getFileUriCompat to avoid being molested by Android
                val uri = getFileUriCompat(applicationContext, file)
                EventBus.getDefault().post(
                    CommunicationEvent(
                        CommunicationEvent.Type.APK_AVAILABLE,
                        message = uri.toString()
                    )
                )
                notificationManager.notifyLast(
                    UpdateInstallNotification(uri)
                )
            }
        } ?: run {
            Timber.d("Download failed")
        }
    }

    @Subscribe
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.UPDATE_WORKER) return
        if (event.type == CommunicationEvent.Type.CANCEL) killSwitch.set(true)
    }

    override fun runProgressNotification() {
        Timber.v("Download progress: %s%%", progressPc.roundToInt())
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                R.id.update_download_service,
                0,
                0, 0, progressPc
            )
        )
        notificationManager.notify(UpdateProgressNotification(progressPc.roundToInt()))
    }
}